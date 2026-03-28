package com.authservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "JWT token response")
public record AuthResponse(
        @Schema(description = "Signed JWT token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token
) {}
