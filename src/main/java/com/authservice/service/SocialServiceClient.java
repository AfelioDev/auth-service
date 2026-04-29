package com.authservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Calls into social-service for cross-service notifications. Used after a
 * user updates their displayName so social-service can fan the change out to
 * the user's friends over their personal WS channels.
 */
@Component
public class SocialServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SocialServiceClient.class);

    private final RestClient restClient;

    public SocialServiceClient(@Value("${social-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Fire-and-forget notification: tells social-service to broadcast a
     * DISPLAY_NAME_CHANGED WS event to the user's own channel and to every
     * accepted friend. Failures are logged but do not block the PUT response.
     */
    public void notifyDisplayNameChanged(Long userId, String displayName) {
        try {
            restClient.post()
                    .uri("/internal/users/displayname-changed")
                    .body(Map.of(
                            "userId", userId,
                            "displayName", displayName == null ? "" : displayName))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Could not notify social-service of displayName change for user {}: {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Fire-and-forget notification: tells social-service to broadcast an
     * AVATAR_CHANGED WS event to the user's own channel and to every accepted
     * friend so other clients reflect the new equipped avatar without a
     * refresh (Tarea 14 / ONE-14, analogous to displayName-changed).
     *
     * Any of {@code avatarId}, {@code imageUrl}, {@code name} may be null
     * when the call is informational only (e.g., the equipped didn't change
     * during an initial-free swap); social-service is expected to skip the
     * broadcast in that case.
     */
    public void notifyAvatarChanged(Long userId, String avatarId, String imageUrl, String name) {
        try {
            java.util.HashMap<String, Object> body = new java.util.HashMap<>();
            body.put("userId", userId);
            body.put("avatarId", avatarId);
            body.put("imageUrl", imageUrl);
            body.put("name", name);
            restClient.post()
                    .uri("/internal/users/avatar-changed")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Could not notify social-service of avatar change for user {}: {}",
                    userId, e.getMessage());
        }
    }
}
