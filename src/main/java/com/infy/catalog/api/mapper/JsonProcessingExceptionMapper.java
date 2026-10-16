package com.infy.catalog.api.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.infy.catalog.api.filter.CorrelationIdFilter;
import com.infy.catalog.api.problem.ErrorCode;
import com.infy.catalog.api.problem.Problems;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Broken JSON or a wrong value type -> 400 MALFORMED_REQUEST. Names the offending field but never
 * echoes Jackson's message (it contains Java class names).
 */
@Provider
public class JsonProcessingExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Context
    private HttpHeaders headers;

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(JsonProcessingException exception) {
        List<String> errors = List.of();
        String detail = "The JSON body is not valid JSON.";
        if (exception instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String field = mapping.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            detail = "The JSON body has a value of the wrong type.";
            errors = List.of(field + " has an invalid value");
        }
        return ProblemResponses.build(
                Problems.of(ErrorCode.MALFORMED_REQUEST, detail, errors, ProblemResponses.instance(uriInfo),
                        CorrelationIdFilter.currentTraceId()),
                headers);
    }
}
