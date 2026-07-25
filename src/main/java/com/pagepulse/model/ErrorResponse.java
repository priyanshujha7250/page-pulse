package com.pagepulse.model;

/**
 * Uniform error body returned for both 4xx and 5xx responses.
 * <p>
 * Keeping errors in a consistent shape means clients never see a raw
 * Spring Boot whitepage or stack trace.
 *
 * @param error Human-readable description of what went wrong
 */
public record ErrorResponse(String error) {}
