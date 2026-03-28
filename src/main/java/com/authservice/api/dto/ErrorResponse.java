package com.authservice.api.dto;

public record ErrorResponse(int status, String error, String message) {}
