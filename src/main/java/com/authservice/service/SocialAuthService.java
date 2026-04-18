package com.authservice.service;

import com.authservice.domain.User;
import com.authservice.domain.UserIdentityRepository;
import com.authservice.domain.UserIdentityRepository.UserIdentity;
import com.authservice.domain.UserRepository;
import com.authservice.service.AppleTokenVerifier.VerifiedAppleUser;
import com.authservice.service.GoogleTokenVerifier.VerifiedGoogleUser;
import com.authservice.service.RefreshTokenService.TokenPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Orchestrates login/signup through external providers (Google, Apple).
 *
 * Flow (both providers):
 *  1. Verify the id_token with the respective verifier (signature + aud + iss).
 *  2. Look up user_identities by (provider, subject). If found → login, issue tokens.
 *  3. Else, if the provider-reported email matches an existing user → auto-link
 *     (link the new provider to that user and issue tokens).
 *  4. Else, create a new user with name/email from the provider and link the identity.
 *
 * Auto-link on verified email is safe because both Google and Apple verify email
 * ownership before issuing the id_token.
 */
@Service
public class SocialAuthService {

    private static final Logger log = LoggerFactory.getLogger(SocialAuthService.class);

    private final GoogleTokenVerifier googleVerifier;
    private final AppleTokenVerifier appleVerifier;
    private final UserRepository userRepo;
    private final UserIdentityRepository identityRepo;
    private final RefreshTokenService refreshTokenService;

    public SocialAuthService(GoogleTokenVerifier googleVerifier,
                             AppleTokenVerifier appleVerifier,
                             UserRepository userRepo,
                             UserIdentityRepository identityRepo,
                             RefreshTokenService refreshTokenService) {
        this.googleVerifier = googleVerifier;
        this.appleVerifier = appleVerifier;
        this.userRepo = userRepo;
        this.identityRepo = identityRepo;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TokenPair loginWithGoogle(String idToken, String deviceId, String deviceName, String ip) {
        VerifiedGoogleUser v = googleVerifier.verify(idToken);
        User user = resolveOrCreate("google", v.subject(), v.email(), v.emailVerified(), v.name());
        return refreshTokenService.issueTokenPair(user, deviceId, deviceName, ip);
    }

    @Transactional
    public TokenPair loginWithApple(String identityToken, String displayName,
                                     String deviceId, String deviceName, String ip) {
        VerifiedAppleUser v = appleVerifier.verify(identityToken);
        // Apple only returns name in the first-ever sign-in response (on the client
        // side). The client can forward it via `displayName`. Emails from Apple are
        // always considered verified; private-relay addresses are accepted as-is
        // since Apple forwards them to the user's real inbox.
        String nameToUse = (displayName != null && !displayName.isBlank()) ? displayName : "Apple User";
        User user = resolveOrCreate("apple", v.subject(), v.email(), true, nameToUse);
        return refreshTokenService.issueTokenPair(user, deviceId, deviceName, ip);
    }

    // ── Core resolution ──────────────────────────────────────────────────────

    private User resolveOrCreate(String provider, String subject, String email,
                                  boolean emailVerified, String nameFromProvider) {
        // 1. Already linked identity — return its user and touch last_used_at.
        Optional<UserIdentity> byIdentity = identityRepo.find(provider, subject);
        if (byIdentity.isPresent()) {
            UserIdentity ident = byIdentity.get();
            identityRepo.touch(ident.id());
            return userRepo.findById(ident.userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Orphaned identity row: user_id=" + ident.userId()));
        }

        // 2. Auto-link by email when the provider-verified email matches an
        //    existing user. Requires that the email is actually verified by the
        //    provider — we don't want random signups to hijack accounts.
        if (email != null && !email.isBlank() && emailVerified) {
            Optional<User> byEmail = userRepo.findByEmail(email);
            if (byEmail.isPresent()) {
                User existing = byEmail.get();
                identityRepo.link(existing.getId(), provider, subject, email);
                log.info("[SOCIAL-AUTH] linked provider={} to existing userId={} via email match",
                        provider, existing.getId());
                return existing;
            }
        }

        // 3. Brand-new user.
        User newUser = new User();
        newUser.setName(nameFromProvider != null ? nameFromProvider : "User");
        newUser.setEmail(email);
        userRepo.save(newUser);
        identityRepo.link(newUser.getId(), provider, subject, email);
        log.info("[SOCIAL-AUTH] created new user via provider={} userId={}",
                provider, newUser.getId());
        return newUser;
    }
}
