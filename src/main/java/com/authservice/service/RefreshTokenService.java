package com.authservice.service;

import com.authservice.domain.RefreshTokenRepository;
import com.authservice.domain.RefreshTokenRepository.RefreshToken;
import com.authservice.domain.User;
import com.authservice.domain.UserRepository;
import com.authservice.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages refresh tokens with OAuth 2.1-style rotation and reuse detection.
 *
 * - On login: issue a new access+refresh pair. The refresh token is a random
 *   256-bit secret; only its SHA-256 hash is stored in the DB.
 * - On refresh: validate, rotate (revoke old + issue new in the same family),
 *   and return a new pair. If the old token was already revoked (reuse
 *   detection), revoke the entire family.
 * - On logout: revoke the specific refresh token.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${jwt.refresh-token-ttl-days:90}")
    private int refreshTtlDays;

    private final RefreshTokenRepository refreshRepo;
    private final UserRepository userRepo;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshRepo,
                                UserRepository userRepo,
                                JwtService jwtService) {
        this.refreshRepo = refreshRepo;
        this.userRepo = userRepo;
        this.jwtService = jwtService;
    }

    // ── Issue (on login/register) ────────────────────────────────────────

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

    public TokenPair issueTokenPair(User user, String deviceId, String deviceName, String ip) {
        String accessToken = jwtService.generateToken(user);
        String rawRefresh = generateRawToken();
        String hash = sha256(rawRefresh);
        UUID familyId = UUID.randomUUID();

        refreshRepo.save(user.getId(), hash, familyId, deviceId, deviceName, ip,
                LocalDateTime.now().plusDays(refreshTtlDays));

        return new TokenPair(accessToken, rawRefresh, jwtService.getExpirationMs() / 1000);
    }

    // ── Refresh (rotation + reuse detection) ─────────────────────────────

    public TokenPair refresh(String rawToken, String deviceId, String deviceName, String ip) {
        String hash = sha256(rawToken);
        RefreshToken rt = refreshRepo.findByHash(hash)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "invalid_refresh_token"));

        // REUSE DETECTION: the token was already revoked by a prior rotation.
        // Someone is replaying it — revoke the entire family.
        if (rt.isRevoked()) {
            refreshRepo.revokeFamily(rt.familyId());
            log.warn("[REFRESH] reuse detected for familyId={} userId={} — revoked entire family",
                    rt.familyId(), rt.userId());
            throw new AppException(HttpStatus.UNAUTHORIZED, "refresh_reuse_detected");
        }

        if (rt.isExpired()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "refresh_expired");
        }

        User user = userRepo.findById(rt.userId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "user_not_found"));

        // Rotate: revoke the old, issue a new one in the same family
        String newRaw = generateRawToken();
        String newHash = sha256(newRaw);
        UUID newId = refreshRepo.save(user.getId(), newHash, rt.familyId(),
                deviceId, deviceName, ip,
                LocalDateTime.now().plusDays(refreshTtlDays));

        refreshRepo.revokeAndReplace(rt.id(), newId);

        String accessToken = jwtService.generateToken(user);
        return new TokenPair(accessToken, newRaw, jwtService.getExpirationMs() / 1000);
    }

    // ── Logout / session management ──────────────────────────────────────

    public void logout(String rawToken) {
        String hash = sha256(rawToken);
        RefreshToken rt = refreshRepo.findByHash(hash).orElse(null);
        if (rt != null && !rt.isRevoked()) {
            refreshRepo.revoke(rt.id());
        }
    }

    public List<Map<String, Object>> listSessions(Long userId, String currentTokenRaw) {
        String currentHash = currentTokenRaw != null ? sha256(currentTokenRaw) : "";
        List<RefreshToken> active = refreshRepo.findActiveByUser(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (RefreshToken rt : active) {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("id", rt.id().toString());
            session.put("deviceName", rt.deviceName());
            session.put("deviceId", rt.deviceId());
            session.put("ip", rt.ip());
            session.put("issuedAt", rt.issuedAt().toString());
            session.put("lastUsedAt", rt.lastUsedAt() != null ? rt.lastUsedAt().toString() : null);
            session.put("current", rt.tokenHash().equals(currentHash));
            result.add(session);
        }
        return result;
    }

    public void revokeSession(Long userId, UUID sessionId) {
        refreshRepo.findActiveByUser(userId).stream()
                .filter(rt -> rt.id().equals(sessionId))
                .findFirst()
                .ifPresent(rt -> refreshRepo.revoke(rt.id()));
    }

    public void revokeAllOthers(Long userId, String currentTokenRaw) {
        String currentHash = currentTokenRaw != null ? sha256(currentTokenRaw) : "";
        refreshRepo.findActiveByUser(userId).stream()
                .filter(rt -> !rt.tokenHash().equals(currentHash))
                .forEach(rt -> refreshRepo.revoke(rt.id()));
    }

    public void revokeAllAndBumpVersion(Long userId) {
        refreshRepo.revokeAllByUser(userId);
        userRepo.incrementTokenVersion(userId);
    }

    // ── Cleanup job ──────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${jwt.refresh-cleanup-interval-ms:3600000}")
    public void purgeExpiredTokens() {
        int deleted = refreshRepo.deleteExpiredAndRevoked(7);
        if (deleted > 0) {
            log.info("[REFRESH-CLEANUP] purged {} expired/revoked refresh tokens", deleted);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
