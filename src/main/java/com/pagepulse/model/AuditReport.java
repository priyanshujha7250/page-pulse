package com.pagepulse.model;

/**
 * Immutable value object that holds the full audit result for a single URL.
 * <p>
 * Uses a record (Java 16+) for conciseness — all fields are final and
 * a canonical constructor + accessors are generated automatically.
 *
 * @param httpStatus        HTTP status code returned by the remote server
 * @param responseTimeMs    Round-trip time in milliseconds (just before → just after fetch)
 * @param title             Content of the {@code <title>} element, or empty string if absent
 * @param metaDescription   Content of {@code <meta name="description">}, or {@code null} if absent
 * @param h1Count           Number of {@code <h1>} elements on the page
 * @param imagesMissingAlt  Number of {@code <img>} tags whose {@code alt} is missing or blank
 * @param totalImages       Total number of {@code <img>} tags on the page
 * @param wordCount         Approximate word count of the visible body text
 */
public record AuditReport(
        int httpStatus,
        long responseTimeMs,
        String title,
        String metaDescription,
        int h1Count,
        int imagesMissingAlt,
        int totalImages,
        int wordCount
) {}
