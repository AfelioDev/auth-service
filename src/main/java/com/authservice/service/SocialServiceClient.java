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
}
