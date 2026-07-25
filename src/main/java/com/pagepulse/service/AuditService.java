package com.pagepulse.service;

import com.pagepulse.exception.AuditException;
import com.pagepulse.model.AuditReport;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Core business logic for auditing a web page.
 * <p>
 * Separation of concerns:
 * <ul>
 *   <li>{@link #audit(String)} — validates the URL and performs the HTTP fetch (network side).</li>
 *   <li>{@link #parseDocument(Document, int, long)} — pure parsing logic that derives the
 *       {@link AuditReport} from an already-fetched Jsoup {@link Document}. This method has
 *       <em>no network dependency</em>, making it trivially unit-testable.</li>
 * </ul>
 */
@Service
public class AuditService {

    /** Jsoup connection timeout in milliseconds (5 seconds as specified). */
    private static final int TIMEOUT_MS = 5_000;

    /**
     * Validates the supplied URL, fetches the page, and returns a full audit report.
     *
     * @param url the raw URL string from the request parameter
     * @return populated {@link AuditReport}
     * @throws AuditException for any expected failure (blank URL, bad URL, timeout, non-HTML)
     */
    public AuditReport audit(String url) {
        // --- 1. Validate: blank / null -----------------------------------------------
        if (url == null || url.isBlank()) {
            throw new AuditException(
                    "The 'url' query parameter is required and must not be blank.",
                    HttpStatus.BAD_REQUEST
            );
        }

        // --- 2. Validate: structural correctness -------------------------------------
        // java.net.URL throws MalformedURLException for structurally invalid strings
        // before any network call is made.
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new AuditException(
                    "The provided url is not a valid URL: " + url,
                    HttpStatus.BAD_REQUEST
            );
        }

        // --- 3. Fetch the page -------------------------------------------------------
        long startTime = System.currentTimeMillis();
        Connection.Response response;
        try {
            response = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    // Mimic a real browser to avoid bot-blocking 403s on common sites
                    .userAgent("Mozilla/5.0 (compatible; PagePulse/1.0; +https://pagepulse.app)")
                    .followRedirects(true)
                    .ignoreHttpErrors(true)  // Let us inspect non-2xx responses rather than throwing
                    .execute();
        } catch (IOException e) {
            // Jsoup wraps a SocketTimeoutException inside an IOException on timeout
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out")) {
                throw new AuditException(
                        "The request timed out after " + (TIMEOUT_MS / 1000) + " seconds.",
                        HttpStatus.BAD_REQUEST
                );
            }
            throw new AuditException(
                    "Failed to fetch the URL: " + msg,
                    HttpStatus.BAD_REQUEST
            );
        }
        long responseTimeMs = System.currentTimeMillis() - startTime;

        // --- 4. Reject non-HTML responses -------------------------------------------
        // Content-Type can look like "text/html; charset=utf-8", so we check the prefix.
        String contentType = response.contentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("text/html")) {
            throw new AuditException(
                    "The URL did not return an HTML page (Content-Type: "
                            + (contentType != null ? contentType : "unknown") + ").",
                    HttpStatus.BAD_REQUEST
            );
        }

        // --- 5. Parse and return -----------------------------------------------------
        // response.parse() declares a checked IOException (e.g. if the response body
        // cannot be read after a successful connection), so it must be caught explicitly.
        Document document;
        try {
            document = response.parse();
        } catch (IOException e) {
            throw new AuditException(
                    "Failed to parse the response body: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return parseDocument(document, response.statusCode(), responseTimeMs);
    }

    /**
     * Derives an {@link AuditReport} from a parsed Jsoup {@link Document}.
     * <p>
     * This method is intentionally free of any network I/O so it can be exercised
     * in unit tests by passing in a {@link Document} built directly from an HTML string.
     *
     * @param doc            the parsed HTML document
     * @param httpStatus     the HTTP status code returned by the remote server
     * @param responseTimeMs the measured round-trip time in milliseconds
     * @return a fully populated {@link AuditReport}
     */
    public AuditReport parseDocument(Document doc, int httpStatus, long responseTimeMs) {
        // Title — <title> text, trimmed; empty string if the element is absent
        String title = doc.title();

        // Meta description — look for <meta name="description" content="...">
        // Returns null (not empty string) if the tag is absent, as specified
        Element metaEl = doc.selectFirst("meta[name=description]");
        String metaDescription = (metaEl != null) ? metaEl.attr("content") : null;

        // H1 count
        int h1Count = doc.select("h1").size();

        // Image audit — count total <img> and those with missing/blank alt
        Elements images = doc.select("img");
        int totalImages = images.size();
        int imagesMissingAlt = 0;
        for (Element img : images) {
            // hasAttr("alt") is false when the attribute is completely absent;
            // attr("alt").isBlank() catches alt="" (empty string).
            if (!img.hasAttr("alt") || img.attr("alt").isBlank()) {
                imagesMissingAlt++;
            }
        }

        // Word count — extract visible text from <body>, split on whitespace
        // Jsoup's text() strips HTML tags and collapses whitespace
        String bodyText = doc.body() != null ? doc.body().text() : "";
        int wordCount = bodyText.isBlank() ? 0 : bodyText.trim().split("\\s+").length;

        return new AuditReport(
                httpStatus,
                responseTimeMs,
                title,
                metaDescription,
                h1Count,
                imagesMissingAlt,
                totalImages,
                wordCount
        );
    }
}
