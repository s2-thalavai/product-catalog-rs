package com.infy.catalog.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds CORS headers for allowed origins. Preflight (OPTIONS) requests are answered by Jersey's
 * built-in OPTIONS handling; this filter decorates that response.
 */
@Provider
public class CorsFilter implements ContainerResponseFilter {

    private final CorsPolicy policy;

    public CorsFilter(CorsPolicy policy) {
        this.policy = policy;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String origin = request.getHeaderString("Origin");
        if (origin == null) {
            return;
        }
        response.getHeaders().add(HttpHeaders.VARY, "Origin");
        boolean preflight = "OPTIONS".equals(request.getMethod())
                && request.getHeaderString("Access-Control-Request-Method") != null;
        policy.responseHeaders(origin, preflight).forEach((name, value) -> response.getHeaders().putSingle(name, value));
    }
}
