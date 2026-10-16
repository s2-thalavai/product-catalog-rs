package com.infy.catalog.api.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

import java.util.Locale;
import java.util.Map;

/**
 * Lets browsers and quick tests pick a representation with ?format=json|xml|html|text, which is
 * translated into an Accept header before content negotiation. The Accept header is the
 * standard mechanism; this is a convenience.
 */
@Provider
@PreMatching
@Priority(Priorities.HEADER_DECORATOR)
public class FormatParameterFilter implements ContainerRequestFilter {

    static final Map<String, String> FORMATS = Map.of(
            "json", "application/json",
            "xml", "application/xml",
            "html", "text/html",
            "text", "text/plain",
            "txt", "text/plain");

    @Override
    public void filter(ContainerRequestContext request) {
        String format = request.getUriInfo().getQueryParameters().getFirst("format");
        if (format == null || format.isBlank()) {
            return;
        }
        String mediaType = mediaTypeFor(format);
        if (mediaType == null) {
            throw new NotAcceptableException("format must be one of json, xml, html, text");
        }
        request.getHeaders().putSingle(HttpHeaders.ACCEPT, mediaType);
    }

    static String mediaTypeFor(String format) {
        return FORMATS.get(format.trim().toLowerCase(Locale.ROOT));
    }
}
