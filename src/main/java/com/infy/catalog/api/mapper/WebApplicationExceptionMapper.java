package com.infy.catalog.api.mapper;

import com.infy.catalog.api.filter.CorrelationIdFilter;
import com.infy.catalog.api.problem.ErrorCode;
import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.api.problem.Problems;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Errors raised by Jersey itself (unknown path, wrong method, content negotiation, unreadable body,
 * failed preconditions) become problem documents too, so clients see one error format everywhere.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    private HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        int status = original.getStatus();
        if (status < 400) {
            return original;   // redirects and 304s pass through untouched
        }
        ErrorCode code = ErrorCode.forHttpStatus(status);
        String title = original.getStatusInfo().getReasonPhrase();
        ProblemDetail problem = Problems.ofStatus(code, status, title == null || title.isBlank() ? code.title() : title,
                detail(code), ProblemResponses.instance(uriInfo), CorrelationIdFilter.currentTraceId());

        Response.ResponseBuilder response = Response.status(status)
                .type(ProblemResponses.mediaType(headers))
                .entity(problem);
        for (String header : List.of(HttpHeaders.ALLOW, "ETag")) {   // keep what the client needs to recover
            Object value = original.getHeaders().getFirst(header);
            if (value != null) {
                response.header(header, value);
            }
        }
        return response.build();
    }

    static String detail(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "No resource at this path. Start at /api/v1/products or /openapi.json.";
            case METHOD_NOT_ALLOWED -> "This HTTP method is not supported here; see the Allow header.";
            case NOT_ACCEPTABLE -> "Cannot produce the requested media type. Supported: application/json, "
                    + "application/xml, text/html, text/plain (or ?format=json|xml|html|text).";
            case UNSUPPORTED_MEDIA_TYPE -> "Send the body as application/json or application/xml "
                    + "(PATCH also accepts application/merge-patch+json).";
            case MALFORMED_REQUEST -> "The request could not be read. Check the body syntax and the Content-Type.";
            case PRECONDITION_FAILED -> "The product changed since you read it (ETag mismatch). "
                    + "GET it again and retry with the new ETag in If-Match.";
            default -> null;
        };
    }
}
