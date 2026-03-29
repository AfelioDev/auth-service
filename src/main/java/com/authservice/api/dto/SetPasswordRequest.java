package com.authservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Set or change account password")
public record SetPasswordRequest(
        @Schema(description = "Current password. Required only if account already has a password.", example = "oldPass123")
        String currentPassword,

        @Schema(description = "New password (min 8 characters)", example = "newPass123")
        @NotBlank(message = "New password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String newPassword
) {}
