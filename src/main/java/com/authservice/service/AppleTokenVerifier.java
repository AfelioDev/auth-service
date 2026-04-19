package com.authservice.service;

import com.authservice.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Set;

/**
 * Validates Apple `identity_token` JWTs issued by "Sign in with Apple".
 *
 * Validation rules:
 *  - Signature valid against Apple's JWKS (https://appleid.apple.com/auth/keys)
 *  - 'iss' equals https://appleid.apple.com
 *  - 'aud' equals either the iOS bundle ID (native iOS flow) or the Services ID
 *     (Android/web flow). Both are accepted so one backend can validate both
 *     client types.
 *  - Not expired
 *
 * The private `.p8` key isn't used here — that's only needed for server-to-server
 * calls (e.g., validating authorization codes or revoking tokens). Token validation
 * uses Apple's public JWKS.
 */
@Service
public class AppleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(AppleTokenVerifier.class);
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";

    @Value("${apple.bundle-id:}")
    private String bundleId;

    @Value("${apple.services-id:}")
    private String servicesId;

    private ConfigurableJWTProcessor<SecurityContext> processor;
    private Set<String> allowedAudiences;

    @PostConstruct
    void init() {
        if ((bundleId == null || bundleId.isBlank()) &&
            (servicesId == null || servicesId.isBlank())) {
            log.warn("[APPLE-AUTH] apple.bundle-id and apple.services-id both empty — /auth/apple will return 503");
            return;
        }

        try {
            com.nimbusds.jose.util.DefaultResourceRetriever resourceRetriever =
                    new com.nimbusds.jose.util.DefaultResourceRetriever(5000, 5000, 51200);

            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                    .create(new URL(APPLE_JWKS_URL), resourceRetriever)
                    .retrying(true)
                    .build();

            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);

            ConfigurableJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
            p.setJWSKeySelector(keySelector);
            this.processor = p;

            // Accept either the iOS bundle or the Services ID as aud.
            Set<String> auds = new java.util.HashSet<>();
            if (bundleId != null && !bundleId.isBlank()) auds.add(bundleId);
            if (servicesId != null && !servicesId.isBlank()) auds.add(servicesId);
            this.allowedAudiences = auds;
        } catch (Exception e) {
            log.error("[APPLE-AUTH] failed to initialize JWKS source: {}", e.getMessage(), e);
        }
    }

    public record VerifiedAppleUser(String subject, String email, boolean emailVerified, boolean privateRelay) {}

    public VerifiedAppleUser verify(String identityToken) {
        if (processor == null) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Sign in with Apple not configured on server");
        }
        if (identityToken == null || identityToken.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "identityToken is required");
        }

        JWTClaimsSet claims;
        try {
            claims = processor.process(identityToken, null);
        } catch (Exception e) {
            log.warn("[APPLE-AUTH] token verification failed: {}", e.getMessage());
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid_apple_token");
        }

        if (!APPLE_ISSUER.equals(claims.getIssuer())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid_apple_issuer");
        }

        boolean audOk = claims.getAudience() != null && claims.getAudience().stream()
                .anyMatch(allowedAudiences::contains);
        if (!audOk) {
            log.warn("[APPLE-AUTH] aud mismatch: token_aud={} allowed={}",
                    claims.getAudience(), allowedAudiences);
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid_apple_audience");
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "apple_token_missing_subject");
        }

        String email = (String) claims.getClaim("email");
        Object emailVerifiedClaim = claims.getClaim("email_verified");
        Object privateRelayClaim = claims.getClaim("is_private_email");

        return new VerifiedAppleUser(
                subject,
                email,
                parseBool(emailVerifiedClaim),
                parseBool(privateRelayClaim));
    }

    /**
     * Apple sometimes sends booleans as the string "true"/"false" instead of JSON booleans.
     */
    private static boolean parseBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(v.toString());
    }
}
