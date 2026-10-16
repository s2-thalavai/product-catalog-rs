package com.infy.catalog.api.problem;

/**
 * Stable, machine-readable error codes (the "errorCode" member of every problem).
 * Clients should branch on these, not on titles or messages.
 */
public enum ErrorCode {
    PRODUCT_NOT_FOUND(404, "Product not found", "product-not-found"),
    DUPLICATE_SKU(409, "Duplicate SKU", "duplicate-sku"),
    VALIDATION_FAILED(400, "Validation failed", "validation-failed"),
    MALFORMED_REQUEST(400, "Malformed request", "malformed-request"),
    PRECONDITION_FAILED(412, "Precondition failed", "precondition-failed"),
    NOT_FOUND(404, "Not Found", null),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed", null),
    NOT_ACCEPTABLE(406, "Not Acceptable", null),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type", null),
    REQUEST_REJECTED(400, "Bad Request", null),
    INTERNAL_ERROR(500, "Internal Server Error", null);

    /** Base of the problem "type" URIs (documentation pages for API-specific problems). */
    public static final String TYPE_BASE = "https://catalog.infy.com/problems/";

    private final int status;
    private final String title;
    private final String slug;

    ErrorCode(int status, String title, String slug) {
        this.status = status;
        this.title = title;
        this.slug = slug;
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** API-specific problems get their own type URI; plain HTTP errors use "about:blank" (RFC 9457 §4.2.1). */
    public String type() {
        return slug == null ? "about:blank" : TYPE_BASE + slug;
    }

    /** Error code for an HTTP error raised by the framework (routing, content negotiation, preconditions). */
    public static ErrorCode forHttpStatus(int status) {
        return switch (status) {
            case 400 -> MALFORMED_REQUEST;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 412 -> PRECONDITION_FAILED;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status >= 500 ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
