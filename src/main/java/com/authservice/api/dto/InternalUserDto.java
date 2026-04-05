package com.authservice.api.dto;

/**
 * Minimal user profile returned by the internal endpoint.
 * Used by social-service and match-service for enriching responses.
 */
public record InternalUserDto(Long id, String displayName, String wcaId, String avatarUrl) {}
