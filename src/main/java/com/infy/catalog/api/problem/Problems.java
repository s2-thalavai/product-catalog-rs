package com.infy.catalog.api.problem;

import com.infy.catalog.domain.exception.CatalogException;
import com.infy.catalog.domain.exception.DuplicateSkuException;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.domain.exception.ProductNotFoundException;

import java.util.List;
import java.util.Locale;

/** Builds {@link ProblemDetail}s and chooses their media type. Framework-free so it is unit-testable. */
public final class Problems {

    public static final String PROBLEM_JSON = "application/problem+json";
    public static final String PROBLEM_XML = "application/problem+xml";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_PLAIN = "text/plain";

    private Problems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail, List<String> errors, String instance, String traceId) {
        return new ProblemDetail(code.type(), code.title(), code.status(), detail, instance, code.name(), traceId, errors);
    }

    /** Same as {@link #of} but keeps the real HTTP status (e.g. 409/410 mapped to REQUEST_REJECTED). */
    public static ProblemDetail ofStatus(ErrorCode code, int status, String title, String detail,
                                         String instance, String traceId) {
        return new ProblemDetail(code.type(), title, status, detail, instance, code.name(), traceId, List.of());
    }

    public static ProblemDetail fromDomain(CatalogException exception, String instance, String traceId) {
        if (exception instanceof ProductNotFoundException e) {
            return of(ErrorCode.PRODUCT_NOT_FOUND, e.getMessage(), List.of(), instance, traceId);
        }
        if (exception instanceof DuplicateSkuException e) {
            return of(ErrorCode.DUPLICATE_SKU, e.getMessage(), List.of(), instance, traceId);
        }
        if (exception instanceof InvalidRequestException e) {
            return of(ErrorCode.VALIDATION_FAILED, "The request has " + e.violations().size()
                    + " invalid field(s); see errors.", e.violations(), instance, traceId);
        }
        return of(ErrorCode.INTERNAL_ERROR, "Unexpected server error.", List.of(), instance, traceId);
    }

    /**
     * Picks the problem representation from the client's acceptable media types (already sorted by
     * preference, as "type/subtype"). JSON wins for wildcards and anything we can't produce.
     */
    public static String mediaTypeFor(List<String> acceptable) {
        for (String raw : acceptable) {
            String type = raw.toLowerCase(Locale.ROOT).trim();
            int params = type.indexOf(';');
            if (params >= 0) {
                type = type.substring(0, params).trim();
            }
            switch (type) {
                case "*/*", "application/*", "application/json", PROBLEM_JSON -> {
                    return PROBLEM_JSON;
                }
                case "application/xml", "text/xml", PROBLEM_XML -> {
                    return PROBLEM_XML;
                }
                case TEXT_HTML, "application/xhtml+xml" -> {
                    return TEXT_HTML;
                }
                case TEXT_PLAIN, "text/*" -> {
                    return TEXT_PLAIN;
                }
                default -> {
                    if (type.endsWith("+json")) {
                        return PROBLEM_JSON;
                    }
                    if (type.endsWith("+xml")) {
                        return PROBLEM_XML;
                    }
                }
            }
        }
        return PROBLEM_JSON;
    }
}
