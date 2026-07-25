package com.pagepulse.controller;

import com.pagepulse.exception.AuditException;
import com.pagepulse.model.AuditReport;
import com.pagepulse.model.ErrorResponse;
import com.pagepulse.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP layer for the Page Pulse audit API.
 * <p>
 * This controller's only responsibilities are:
 * <ol>
 *   <li>Accept the incoming request and extract query parameters.</li>
 *   <li>Delegate all business logic to {@link AuditService}.</li>
 *   <li>Translate {@link AuditException} (expected failures) into clean JSON error responses.</li>
 *   <li>Translate any unexpected {@link Exception} into a generic 500 — never exposing internals.</li>
 * </ol>
 */
@RestController
public class AuditController {

    private static final Logger log = LoggerFactory.getLogger(AuditController.class);

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Audits the page at the given URL.
     *
     * @param url the fully-qualified URL to audit (e.g. {@code https://example.com})
     * @return 200 with {@link AuditReport} JSON on success
     */
    @GetMapping("/audit")
    public ResponseEntity<AuditReport> audit(@RequestParam(required = false) String url) {
        AuditReport report = auditService.audit(url);
        return ResponseEntity.ok(report);
    }

    // -------------------------------------------------------------------------
    // Exception handlers — these two methods cover every possible failure path
    // -------------------------------------------------------------------------

    /**
     * Handles all anticipated failures ({@link AuditException}) raised by the service.
     * Returns the status code embedded in the exception, plus a clean JSON error body.
     */
    @ExceptionHandler(AuditException.class)
    public ResponseEntity<ErrorResponse> handleAuditException(AuditException ex) {
        log.warn("Audit failed ({}): {}", ex.getStatus(), ex.getMessage());
        return ResponseEntity
                .status(ex.getStatus())
                .body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * Catch-all for any unexpected runtime exceptions.
     * Logs the full stack trace server-side but returns only a generic message to the client,
     * so internal details are never leaked in a production response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error during audit", ex);
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponse("An unexpected error occurred. Please try again later."));
    }
}
