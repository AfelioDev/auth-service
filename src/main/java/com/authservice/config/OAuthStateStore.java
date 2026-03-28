package com.authservice.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for OAuth2 CSRF state parameters.
 * Each entry expires after 10 minutes.
 *
 * Note: for multi-instance deployments, replace with a shared store (Redis, DB).
 */
@Component
public class OAuthStateStore {

    private static final long TTL_SECONDS = 600;

    public record StateEntry(String flow, Long linkUserId, String redirectUri, Instant createdAt) {}

    private final ConcurrentHashMap<String, StateEntry> store = new ConcurrentHashMap<>();

    /**
     * @param flow        "LOGIN" or "LINK"
     * @param linkUserId  user ID to link (only for LINK flow, null otherwise)
     * @param redirectUri where to send the token after a successful login (null = use server default)
     * @return the generated opaque state value
     */
    public String newState(String flow, Long linkUserId, String redirectUri) {
        String state = UUID.randomUUID().toString();
        // Evict expired entries on write
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        store.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
        store.put(state, new StateEntry(flow, linkUserId, redirectUri, Instant.now()));
        return state;
    }

    /**
     * Validates and consumes a state value (one-time use).
     */
    public Optional<StateEntry> consume(String state) {
        if (state == null) return Optional.empty();
        StateEntry entry = store.remove(state);
        if (entry == null) return Optional.empty();
        if (entry.createdAt().isBefore(Instant.now().minusSeconds(TTL_SECONDS))) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }
}
