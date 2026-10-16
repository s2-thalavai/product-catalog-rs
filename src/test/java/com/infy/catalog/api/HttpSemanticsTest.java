package com.infy.catalog.api;

import com.infy.catalog.api.filter.CorsPolicy;
import com.infy.catalog.domain.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pagination links, ETags and CORS decisions. */
class HttpSemanticsTest {

    @Test
    void firstPageLinksToNextAndLast() {
        assertEquals(Map.of("first", 0, "next", 1, "last", 2), PageLinks.relations(0, 3));
        assertEquals(List.of("first", "next", "last"), List.copyOf(PageLinks.relations(0, 3).keySet()));
    }

    @Test
    void middleAndLastPagesLinkBackwards() {
        assertEquals(List.of("first", "prev", "next", "last"), List.copyOf(PageLinks.relations(1, 3).keySet()));
        assertEquals(Map.of("first", 0, "prev", 1, "last", 2), PageLinks.relations(2, 3));
    }

    @Test
    void emptyResultAndPagePastTheEnd() {
        assertEquals(Map.of("first", 0, "last", 0), PageLinks.relations(0, 0));
        assertEquals(Map.of("first", 0, "prev", 2, "last", 2), PageLinks.relations(9, 3));
    }

    @Test
    void etagChangesWhenTheProductChanges() {
        Instant t = Instant.parse("2026-10-16T03:00:00.123456Z");
        Product v1 = product(7, t);
        Product v2 = product(7, t.plusNanos(1_000));

        assertEquals(Etags.of(v1), Etags.of(product(7, t)));
        assertNotEquals(Etags.of(v1), Etags.of(v2));
        assertNotEquals(Etags.of(v1), Etags.of(product(8, t)));
        assertTrue(Etags.of(v1).matches("[0-9a-f]+-[0-9a-f]+"));
    }

    @Test
    void corsOnlyForConfiguredOrigins() {
        CorsPolicy policy = new CorsPolicy(List.of("https://shop.example.com"));

        assertTrue(policy.allows("https://shop.example.com"));
        assertFalse(policy.allows("https://evil.example.com"));
        assertFalse(policy.allows(null));
        assertTrue(policy.responseHeaders("https://evil.example.com", false).isEmpty());

        Map<String, String> simple = policy.responseHeaders("https://shop.example.com", false);
        assertEquals("https://shop.example.com", simple.get("Access-Control-Allow-Origin"));
        assertTrue(simple.get("Access-Control-Expose-Headers").contains("Link"));
        assertFalse(simple.containsKey("Access-Control-Allow-Credentials"));

        Map<String, String> preflight = policy.responseHeaders("https://shop.example.com", true);
        assertTrue(preflight.get("Access-Control-Allow-Methods").contains("PATCH"));
        assertTrue(preflight.get("Access-Control-Allow-Headers").contains("If-Match"));
    }

    @Test
    void corsDisabledWhenNoOriginsConfigured() {
        assertTrue(new CorsPolicy(List.of()).responseHeaders("http://localhost:3000", true).isEmpty());
    }

    private static Product product(long id, Instant updatedAt) {
        return new Product(id, "SKU-1", "Lamp", null, "Home", new BigDecimal("10.00"), 1, updatedAt, updatedAt);
    }
}
