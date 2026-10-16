package com.infy.catalog.api.mapper;

import com.infy.catalog.api.filter.CorrelationIdFilter;
import com.infy.catalog.api.problem.ErrorCode;
import com.infy.catalog.api.problem.Problems;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Anything unexpected -> 500 with a traceId. The details go to the log only. */
@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionMapper.class);

    @Context
    private HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        log.error("Unhandled exception", exception);
        return ProblemResponses.build(
                Problems.of(ErrorCode.INTERNAL_ERROR, "Unexpected server error. Quote the traceId when reporting it.",
                        List.of(), ProblemResponses.instance(uriInfo), CorrelationIdFilter.currentTraceId()),
                headers);
    }
}
