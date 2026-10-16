package com.infy.catalog.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

/** Baseline security headers; HTML pages also get a strict Content-Security-Policy (no scripts at all). */
@Provider
public class SecurityHeadersFilter implements ContainerResponseFilter {

    static final String HTML_CSP = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'";

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        response.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        response.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        MediaType type = response.getMediaType();
        if (type != null && type.isCompatible(MediaType.TEXT_HTML_TYPE)) {
            response.getHeaders().putSingle("Content-Security-Policy", HTML_CSP);
        }
    }
}
