package com.infy.catalog.api.mapper;

import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.api.problem.Problems;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.List;

/** Shared by the exception mappers: picks the problem media type and builds the response. */
final class ProblemResponses {

    private ProblemResponses() {
    }

    static Response build(ProblemDetail problem, HttpHeaders headers) {
        return Response.status(problem.getStatus())
                .type(mediaType(headers))
                .entity(problem)
                .build();
    }

    static String mediaType(HttpHeaders headers) {
        List<String> acceptable;
        try {
            acceptable = headers.getAcceptableMediaTypes().stream().map(MediaType::toString).toList();
        } catch (RuntimeException unparseableAcceptHeader) {
            acceptable = List.of();
        }
        return Problems.mediaTypeFor(acceptable);
    }

    static String instance(UriInfo uriInfo) {
        try {
            return uriInfo.getRequestUri().getRawPath();
        } catch (RuntimeException outsideRequestScope) {
            return null;
        }
    }
}
