package com.infy.catalog.api;

import com.infy.catalog.api.problem.ErrorCode;
import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.api.problem.Problems;
import com.infy.catalog.domain.exception.DuplicateSkuException;
import com.infy.catalog.domain.exception.InvalidRequestException;
import com.infy.catalog.domain.exception.ProductNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** RFC 9457 problem mapping and media-type selection for errors. */
class ProblemsTest {

    @Test
    void domainExceptionsMapToStatusCodeAndType() {
        ProblemDetail notFound = Problems.fromDomain(new ProductNotFoundException(42), "/api/v1/products/42", "t-1");
        assertEquals(404, notFound.getStatus());
        assertEquals("PRODUCT_NOT_FOUND", notFound.getErrorCode());
        assertEquals("https://catalog.infy.com/problems/product-not-found", notFound.getType());
        assertEquals("/api/v1/products/42", notFound.getInstance());
        assertEquals("t-1", notFound.getTraceId());
        assertTrue(notFound.getErrors().isEmpty());

        ProblemDetail duplicate = Problems.fromDomain(new DuplicateSkuException("SKU-1"), null, "t-2");
        assertEquals(409, duplicate.getStatus());
        assertEquals("DUPLICATE_SKU", duplicate.getErrorCode());

        ProblemDetail invalid = Problems.fromDomain(
                new InvalidRequestException(List.of("name is required", "price must be >= 0")), null, "t-3");
        assertEquals(400, invalid.getStatus());
        assertEquals("VALIDATION_FAILED", invalid.getErrorCode());
        assertEquals(List.of("name is required", "price must be >= 0"), invalid.getErrors());
        assertTrue(invalid.getDetail().contains("2 invalid field(s)"));
    }

    @Test
    void frameworkStatusesMapToErrorCodes() {
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.forHttpStatus(404));
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.forHttpStatus(405));
        assertEquals(ErrorCode.NOT_ACCEPTABLE, ErrorCode.forHttpStatus(406));
        assertEquals(ErrorCode.PRECONDITION_FAILED, ErrorCode.forHttpStatus(412));
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.forHttpStatus(415));
        assertEquals(ErrorCode.MALFORMED_REQUEST, ErrorCode.forHttpStatus(400));
        assertEquals(ErrorCode.REQUEST_REJECTED, ErrorCode.forHttpStatus(429));
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forHttpStatus(503));
        assertEquals("about:blank", ErrorCode.NOT_FOUND.type());
    }

    @Test
    void problemMediaTypeFollowsTheAcceptHeader() {
        assertEquals(Problems.PROBLEM_JSON, Problems.mediaTypeFor(List.of()));
        assertEquals(Problems.PROBLEM_JSON, Problems.mediaTypeFor(List.of("*/*")));
        assertEquals(Problems.PROBLEM_JSON, Problems.mediaTypeFor(List.of("application/json")));
        assertEquals(Problems.PROBLEM_XML, Problems.mediaTypeFor(List.of("application/xml")));
        assertEquals(Problems.PROBLEM_XML, Problems.mediaTypeFor(List.of("text/xml; charset=utf-8")));
        assertEquals(Problems.TEXT_HTML,
                Problems.mediaTypeFor(List.of("text/html", "application/xhtml+xml", "application/xml;q=0.9", "*/*;q=0.8")));
        assertEquals(Problems.TEXT_PLAIN, Problems.mediaTypeFor(List.of("text/plain")));
        assertEquals(Problems.PROBLEM_JSON, Problems.mediaTypeFor(List.of("image/png")));
        assertEquals(Problems.PROBLEM_JSON, Problems.mediaTypeFor(List.of("image/png", "application/vnd.api+json")));
    }
}
