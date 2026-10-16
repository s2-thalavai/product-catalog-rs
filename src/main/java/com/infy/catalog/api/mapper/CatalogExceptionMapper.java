package com.infy.catalog.api.mapper;

import com.infy.catalog.api.filter.CorrelationIdFilter;
import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.api.problem.Problems;
import com.infy.catalog.domain.exception.CatalogException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Business failures -> 404 PRODUCT_NOT_FOUND, 409 DUPLICATE_SKU, 400 VALIDATION_FAILED. */
@Provider
public class CatalogExceptionMapper implements ExceptionMapper<CatalogException> {

    private static final Logger log = LoggerFactory.getLogger(CatalogExceptionMapper.class);

    @Context
    private HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(CatalogException exception) {
        ProblemDetail problem = Problems.fromDomain(exception, ProblemResponses.instance(uriInfo),
                CorrelationIdFilter.currentTraceId());
        log.info("Request rejected: {} {} {}", problem.getStatus(), problem.getErrorCode(), problem.getErrors());
        return ProblemResponses.build(problem, headers);
    }
}
