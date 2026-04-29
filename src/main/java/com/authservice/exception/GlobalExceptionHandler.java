package com.authservice.exception;

import com.authservice.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(
                        ex.getStatus().value(),
                        ex.getStatus().getReasonPhrase(),
                        ex.getMessage()));
    }

    /**
     * Structured 403 for banned users (Tarea 5 / ONE-9). The client looks for
     * {@code code == "ACCOUNT_BANNED"} to show a blocking dialog with the
     * reason and the optional {@code bannedUntil} timestamp; clients on older
     * versions just see an opaque 403 and surface "Forbidden".
     */
    @ExceptionHandler(BannedException.class)
    public ResponseEntity<Map<String, Object>> handleBanned(BannedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 403);
        body.put("error", "Forbidden");
        body.put("code", "ACCOUNT_BANNED");
        body.put("message", "Account is banned");
        body.put("reason", ex.getReason());
        body.put("bannedUntil", ex.getBannedUntil() == null ? null : ex.getBannedUntil().toString());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(400)
                .body(new ErrorResponse(400, "Validation Failed", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        return ResponseEntity.status(500)
                .body(new ErrorResponse(500, "Internal Server Error", "An unexpected error occurred"));
    }
}
