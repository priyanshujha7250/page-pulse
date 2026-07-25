package com.pagepulse.service;

import com.pagepulse.exception.AuditException;
import com.pagepulse.model.AuditReport;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AuditService}.
 * <p>
 * All tests that exercise parsing use {@link Jsoup#parse(String)} to build a
 * {@link Document} from an inline HTML string, then call
 * {@link AuditService#parseDocument(Document, int, long)} directly.
 * This means <em>no network calls are made</em> in any test.
 */
class AuditServiceTest {

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService();
    }

    // =========================================================================
    // Happy-path parsing tests
    // =========================================================================

    @Test
    @DisplayName("Happy path: all report fields are parsed correctly from well-formed HTML")
    void happyPath_allFieldsParsedCorrectly() {
        String html = """
                <!DOCTYPE html>
                <html>
                  <head>
                    <title>Hello World</title>
                    <meta name="description" content="A great page about testing.">
                  </head>
                  <body>
                    <h1>Main Heading</h1>
                    <h1>Second Heading</h1>
                    <img src="a.png" alt="Logo">
                    <img src="b.png" alt="">
                    <img src="c.png">
                    <p>The quick brown fox jumps over the lazy dog</p>
                  </body>
                </html>
                """;

        Document doc = Jsoup.parse(html);
        AuditReport report = service.parseDocument(doc, 200, 123L);

        assertAll(
                () -> assertEquals(200, report.httpStatus(),         "HTTP status"),
                () -> assertEquals(123L, report.responseTimeMs(),    "Response time"),
                () -> assertEquals("Hello World", report.title(),    "Title"),
                () -> assertEquals("A great page about testing.", report.metaDescription(), "Meta description"),
                () -> assertEquals(2, report.h1Count(),              "H1 count"),
                () -> assertEquals(3, report.totalImages(),          "Total images"),
                // b.png has alt="" (blank) and c.png has no alt attribute → 2 missing
                () -> assertEquals(2, report.imagesMissingAlt(),     "Images missing alt"),
                () -> assertTrue(report.wordCount() > 0,             "Word count should be > 0")
        );
    }

    @Test
    @DisplayName("Missing meta description is reported as null, not an empty string")
    void missingMetaDescription_returnsNull() {
        String html = """
                <html>
                  <head><title>No Meta</title></head>
                  <body><p>Some text</p></body>
                </html>
                """;

        Document doc = Jsoup.parse(html);
        AuditReport report = service.parseDocument(doc, 200, 50L);

        assertNull(report.metaDescription(),
                "metaDescription must be null when the <meta name=\"description\"> tag is absent");
    }

    @Test
    @DisplayName("Page with no images reports zero for both image counts")
    void noImages_bothImageCountsAreZero() {
        String html = "<html><body><p>Text only page</p></body></html>";
        Document doc = Jsoup.parse(html);
        AuditReport report = service.parseDocument(doc, 200, 10L);

        assertEquals(0, report.totalImages());
        assertEquals(0, report.imagesMissingAlt());
    }

    @Test
    @DisplayName("All images have valid alt text: imagesMissingAlt should be 0")
    void allImagesHaveAlt_missingAltIsZero() {
        String html = """
                <html><body>
                  <img src="x.png" alt="image one">
                  <img src="y.png" alt="image two">
                </body></html>
                """;
        Document doc = Jsoup.parse(html);
        AuditReport report = service.parseDocument(doc, 200, 20L);

        assertEquals(2, report.totalImages());
        assertEquals(0, report.imagesMissingAlt());
    }

    // =========================================================================
    // Failure-case tests (validation, before any network call)
    // =========================================================================

    @Test
    @DisplayName("Blank url throws AuditException with a message mentioning 'url'")
    void blankUrl_throwsAuditExceptionMentioningUrl() {
        AuditException ex = assertThrows(AuditException.class,
                () -> service.audit("   "),
                "Expected AuditException for a blank url"
        );

        // The message must reference the offending parameter so the caller knows what to fix
        assertTrue(ex.getMessage().toLowerCase().contains("url"),
                "Exception message should mention 'url', was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Null url throws AuditException with a message mentioning 'url'")
    void nullUrl_throwsAuditExceptionMentioningUrl() {
        AuditException ex = assertThrows(AuditException.class,
                () -> service.audit(null),
                "Expected AuditException for a null url"
        );

        assertTrue(ex.getMessage().toLowerCase().contains("url"),
                "Exception message should mention 'url', was: " + ex.getMessage());
    }

    @Test
    @DisplayName("Structurally malformed URL throws AuditException before any network call")
    void malformedUrl_throwsAuditExceptionWithoutNetworkCall() {
        // "not-a-url-at-all" has no protocol and is structurally invalid for java.net.URL
        AuditException ex = assertThrows(AuditException.class,
                () -> service.audit("not-a-url-at-all"),
                "Expected AuditException for a structurally invalid URL"
        );

        // Should be a 400-level error, not a 5xx
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getStatus(),
                "Malformed URL should produce a 400 BAD_REQUEST");
    }
}
