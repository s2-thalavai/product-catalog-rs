package com.infy.catalog.api.filter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CORS rules: only the explicitly configured origins (CORS_ALLOWED_ORIGINS) get CORS headers.
 * No wildcard, no credentials. An empty list disables CORS entirely (same-origin only).
 */
public final class CorsPolicy {

    static final String ALLOWED_METHODS = "GET, POST, PUT, PATCH, DELETE, OPTIONS";
    static final String ALLOWED_HEADERS = "Accept, Content-Type, If-Match, If-None-Match, X-Correlation-Id";
    static final String EXPOSED_HEADERS = "Location, Link, ETag, X-Total-Count, X-Correlation-Id";
    static final String MAX_AGE_SECONDS = "600";

    private final Set<String> allowedOrigins;

    public CorsPolicy(List<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    public boolean allows(String origin) {
        return origin != null && allowedOrigins.contains(origin);
    }

    /** Headers to add to the response; empty when the origin is not allowed (the browser then blocks it). */
    public Map<String, String> responseHeaders(String origin, boolean preflight) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (!allows(origin)) {
            return headers;
        }
        headers.put("Access-Control-Allow-Origin", origin);
        if (preflight) {
            headers.put("Access-Control-Allow-Methods", ALLOWED_METHODS);
            headers.put("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            headers.put("Access-Control-Max-Age", MAX_AGE_SECONDS);
        } else {
            headers.put("Access-Control-Expose-Headers", EXPOSED_HEADERS);
        }
        return headers;
    }
}
