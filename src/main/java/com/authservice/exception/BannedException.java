package com.authservice.exception;

import java.time.OffsetDateTime;

/**
 * Thrown when a banned user attempts to log in or call an authenticated
 * endpoint (Tarea 5 / ONE-9). The global exception handler renders this
 * with a 403 and a structured payload (code/reason/bannedUntil) so the
 * client can show a tailored dialog instead of a generic error.
 */
public class BannedException extends RuntimeException {

    private final String reason;
    private final OffsetDateTime bannedUntil;

    public BannedException(String reason, OffsetDateTime bannedUntil) {
        super("Account is banned");
        this.reason = reason;
        this.bannedUntil = bannedUntil;
    }

    public String getReason() { return reason; }
    public OffsetDateTime getBannedUntil() { return bannedUntil; }
}
