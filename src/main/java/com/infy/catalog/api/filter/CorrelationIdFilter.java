package com.infy.catalog.api.filter;

import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Takes the caller's X-Correlation-Id (or generates one), puts it in the logging MDC as traceId,
 * echoes it in the response, and writes one access-log line per request. Bodies are never logged.
 */
@Provider
@PreMatching
@Priority(100)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger("access");
    private static final String START = CorrelationIdFilter.class.getName() + ".start";
    /** Short, safe ids only: callers must not be able to inject text into our logs. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    public void filter(ContainerRequestContext request) {
        String traceId = safeIdOrNew(request.getHeaderString(HEADER));
        MDC.put(MDC_KEY, traceId);
        request.setProperty(MDC_KEY, traceId);
        request.setProperty(START, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object traceId = request.getProperty(MDC_KEY);
        if (traceId != null) {
            response.getHeaders().putSingle(HEADER, traceId);
        }
        long durationMs = request.getProperty(START) instanceof Long start ? (System.nanoTime() - start) / 1_000_000 : -1;
        log.info("{} {} -> {} ({} ms, {})", request.getMethod(), request.getUriInfo().getRequestUri().getRawPath(),
                response.getStatus(), durationMs, response.getMediaType());
        MDC.remove(MDC_KEY);
    }

    static String safeIdOrNew(String candidate) {
        if (candidate != null && SAFE_ID.matcher(candidate.trim()).matches()) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString();
    }

    /** Current request's traceId (set by this filter). */
    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "n/a" : traceId;
    }
}
