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

        @Schema(description = "WCA numeric account ID (present for all WCA OAuth users)", example = "371629", nullable = true)
        Long wcaAccountId,

        @Schema(description = "WCA competitor ID — only set if the user has competed at a WCA competition", example = "2009ZEMD01", nullable = true)
        String wcaId,

        @Schema(description = "Whether a WCA account is linked", example = "true")
        boolean wcaLinked,

        @Schema(description = "Whether the account has a password set", example = "false")
        boolean hasPassword,

        @Schema(description = "Account creation timestamp")
        LocalDateTime createdAt,

        @Schema(description = "Public 8-digit code used to add this user as a friend (ONE-40)", example = "47281926")
        String friendCode
) {}
