package com.infy.catalog.api.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltersTest {

    @Test
    void formatParameterMapsToMediaTypes() {
        assertEquals("application/json", FormatParameterFilter.mediaTypeFor("json"));
        assertEquals("application/xml", FormatParameterFilter.mediaTypeFor(" XML "));
        assertEquals("text/html", FormatParameterFilter.mediaTypeFor("html"));
        assertEquals("text/plain", FormatParameterFilter.mediaTypeFor("text"));
        assertEquals("text/plain", FormatParameterFilter.mediaTypeFor("txt"));
        assertNull(FormatParameterFilter.mediaTypeFor("pdf"));
    }

    @Test
    void correlationIdKeepsSafeIdsAndReplacesUnsafeOnes() {
        assertEquals("order-123_A.b", CorrelationIdFilter.safeIdOrNew("order-123_A.b"));
        String injected = "abc\nFAKE LOG LINE";
        assertNotEquals(injected, CorrelationIdFilter.safeIdOrNew(injected));
        assertTrue(CorrelationIdFilter.safeIdOrNew(null).matches("[0-9a-f-]{36}"));
        assertTrue(CorrelationIdFilter.safeIdOrNew("x".repeat(65)).matches("[0-9a-f-]{36}"));
    }

    @Test
    void htmlResponsesGetAScriptFreeContentSecurityPolicy() {
        assertTrue(SecurityHeadersFilter.HTML_CSP.startsWith("default-src 'none'"));
    }
}
