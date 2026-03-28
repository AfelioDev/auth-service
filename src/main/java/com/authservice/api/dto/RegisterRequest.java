package com.authservice.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Registration data")
public record RegisterRequest(
        @Schema(description = "Display name", example = "Feliks Zemdegs")
        @NotBlank(message = "Name is required")
        String name,

        @Schema(description = "Email address", example = "felix@example.com")
        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email,

        @Schema(description = "Password (minimum 8 characters)", example = "s3cr3tpass")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @NotBlank(message = "Password is required")
        String password
) {}
