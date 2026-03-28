package com.authservice.api.dto;

import java.time.LocalDateTime;

public record UserDto(
        Long id,
        String name,
        String email,
        String wcaId,
        boolean wcaLinked,
        boolean hasPassword,
        LocalDateTime createdAt
) {}
