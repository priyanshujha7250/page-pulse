package com.pagepulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown for expected, user-facing failures (bad URL, timeout, non-HTML content, etc.).
 * <p>
 * Carries an {@link HttpStatus} so the controller can return the right HTTP code
 * without needing to know the reason behind the failure.
 */
public class AuditException extends RuntimeException {

    private final HttpStatus status;

    public AuditException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
