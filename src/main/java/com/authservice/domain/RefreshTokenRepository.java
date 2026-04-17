package com.authservice.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private final JdbcTemplate jdbc;

    public RefreshTokenRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RefreshToken(
            UUID id, Long userId, String tokenHash, UUID familyId,
            String deviceId, String deviceName, String ip,
            LocalDateTime issuedAt, LocalDateTime expiresAt,
            LocalDateTime lastUsedAt, LocalDateTime revokedAt,
            UUID replacedBy
    ) {
        public boolean isRevoked() { return revokedAt != null; }
        public boolean isExpired() { return expiresAt.isBefore(LocalDateTime.now()); }
    }

    public Optional<RefreshToken> findByHash(String tokenHash) {
        List<RefreshToken> rows = jdbc.query(
                "SELECT * FROM refresh_tokens WHERE token_hash = ?", mapper(), tokenHash);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public UUID save(Long userId, String tokenHash, UUID familyId,
                     String deviceId, String deviceName, String ip,
                     LocalDateTime expiresAt) {
        return jdbc.queryForObject(
                "INSERT INTO refresh_tokens (user_id, token_hash, family_id, device_id, " +
                "  device_name, ip, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
                UUID.class, userId, tokenHash, familyId, deviceId, deviceName, ip,
                Timestamp.valueOf(expiresAt));
    }

    public void revoke(UUID id) {
        jdbc.update("UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = ?", id);
    }

    public void revokeAndReplace(UUID id, UUID replacedBy) {
        jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = NOW(), last_used_at = NOW(), replaced_by = ? WHERE id = ?",
                replacedBy, id);
    }

    public void revokeFamily(UUID familyId) {
        jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = NOW() WHERE family_id = ? AND revoked_at IS NULL",
                familyId);
    }

    public void revokeAllByUser(Long userId) {
        jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL",
                userId);
    }

    public void revokeAllByUserExcept(Long userId, UUID exceptId) {
        jdbc.update(
                "UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND id <> ? AND revoked_at IS NULL",
                userId, exceptId);
    }

    public List<RefreshToken> findActiveByUser(Long userId) {
        return jdbc.query(
                "SELECT * FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL " +
                "ORDER BY last_used_at DESC NULLS LAST, issued_at DESC",
                mapper(), userId);
    }

    public int deleteExpiredAndRevoked(int olderThanDays) {
        return jdbc.update(
                "DELETE FROM refresh_tokens WHERE " +
                "(revoked_at IS NOT NULL AND revoked_at < NOW() - CAST(? || ' days' AS INTERVAL)) OR " +
                "(expires_at < NOW() - CAST(? || ' days' AS INTERVAL))",
                String.valueOf(olderThanDays), String.valueOf(olderThanDays));
    }

    private RowMapper<RefreshToken> mapper() {
        return (rs, n) -> new RefreshToken(
                UUID.fromString(rs.getString("id")),
                rs.getLong("user_id"),
                rs.getString("token_hash"),
                UUID.fromString(rs.getString("family_id")),
                rs.getString("device_id"),
                rs.getString("device_name"),
                rs.getString("ip"),
                rs.getTimestamp("issued_at").toLocalDateTime(),
                rs.getTimestamp("expires_at").toLocalDateTime(),
                rs.getTimestamp("last_used_at") != null ? rs.getTimestamp("last_used_at").toLocalDateTime() : null,
                rs.getTimestamp("revoked_at") != null ? rs.getTimestamp("revoked_at").toLocalDateTime() : null,
                rs.getString("replaced_by") != null ? UUID.fromString(rs.getString("replaced_by")) : null
        );
    }
}
