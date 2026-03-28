package com.authservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Authenticated user profile")
public record UserDto(
        @Schema(description = "Internal user ID", example = "1")
        Long id,

        @Schema(description = "Display name", example = "Feliks Zemdegs")
        String name,

        @Schema(description = "Email address (null for WCA-only accounts)", example = "felix@example.com", nullable = true)
        String email,

        @Schema(description = "WCA competitor ID (null if not linked)", example = "2009ZEMD01", nullable = true)
        String wcaId,

        @Schema(description = "Whether a WCA account is linked", example = "true")
        boolean wcaLinked,

        @Schema(description = "Whether the account has a password set", example = "false")
        boolean hasPassword,

        @Schema(description = "Account creation timestamp")
        LocalDateTime createdAt
) {}
