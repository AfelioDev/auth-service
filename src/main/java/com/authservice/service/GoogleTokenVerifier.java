package com.authservice.service;

import com.authservice.exception.AppException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Validates Google id_tokens issued to the One Timer app.
 *
 * Validation rules:
 *  - Signature valid against Google's JWKS (handled by GoogleIdTokenVerifier)
 *  - 'aud' equals the registered Web Client ID (`google.client-id`)
 *  - 'iss' is https://accounts.google.com or accounts.google.com
 *  - Not expired
 *
 * The same Web Client ID is used across iOS, Android, and web because that's
 * the `serverClientId` that Google's sign-in plugins use to request id_tokens
 * with a consistent audience.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

    @Value("${google.client-id:}")
    private String clientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void init() {
        if (clientId == null || clientId.isBlank()) {
            log.warn("[GOOGLE-AUTH] google.client-id not configured — /auth/google will return 503");
            return;
        }
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public record VerifiedGoogleUser(String subject, String email, boolean emailVerified, String name) {}

    public VerifiedGoogleUser verify(String idToken) {
        if (verifier == null) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE, "Google Sign-In not configured on server");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "idToken is required");
        }

        GoogleIdToken parsed;
        try {
            parsed = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("[GOOGLE-AUTH] token verification failed: {}", e.getMessage());
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid_google_token");
        }
        if (parsed == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "invalid_google_token");
        }

        Payload payload = parsed.getPayload();
        String subject = payload.getSubject();
        String email = payload.getEmail();
        Boolean verifiedFlag = payload.getEmailVerified();
        String name = (String) payload.get("name");

        if (subject == null || subject.isBlank()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "google_token_missing_subject");
        }

        return new VerifiedGoogleUser(
                subject,
                email,
                verifiedFlag != null && verifiedFlag,
                name);
    }
}
