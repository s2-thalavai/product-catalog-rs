package com.infy.catalog.api;

import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.api.problem.ErrorCode;
import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.api.problem.Problems;
import com.infy.catalog.api.render.HtmlRenderer;
import com.infy.catalog.api.render.QueryStrings;
import com.infy.catalog.api.render.TextRenderer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTML and plain-text representations. */
class RenderingTest {

    private static final Instant T = Instant.parse("2026-10-16T03:00:00Z");
    private static final ProductResponse MOUSE =
            new ProductResponse(1, "ELEC-MOUSE-01", "Wireless Mouse", "2.4 GHz", "Electronics", new BigDecimal("799.00"), 120, T, T);
    private static final ProductResponse EVIL =
            new ProductResponse(2, "EVIL-01", "<script>alert('x')</script>", null, null, new BigDecimal("1.00"), 0, T, T);

    @Test
    void htmlListShowsRowsSearchFormAndPagination() {
        ProductPage page = new ProductPage(List.of(MOUSE), 1, 1, 3, 3, true, true);
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("q", List.of("mouse"));
        query.put("sort", List.of("price,desc"));
        query.put("page", List.of("1"));

        String html = HtmlRenderer.page(page, "/api/v1/products", query);

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<a href=\"/api/v1/products/1\">Wireless Mouse</a>"));
        assertTrue(html.contains("value=\"mouse\""), "search form keeps the keyword");
        assertTrue(html.contains("<option value=\"price,desc\" selected>"), "current sort is selected");
        assertTrue(html.contains("/api/v1/products?q=mouse&amp;sort=price%2Cdesc&amp;page=2"), "next link keeps filters");
        assertTrue(html.contains("/api/v1/products?q=mouse&amp;sort=price%2Cdesc&amp;page=0"), "first/prev link");
        assertTrue(html.contains("Page 2 of 3"));
        assertTrue(html.contains("format=json"));
    }

    @Test
    void htmlEscapesEverythingFromTheData() {
        String html = HtmlRenderer.page(new ProductPage(List.of(EVIL), 0, 20, 1, 1, false, false), "/api/v1/products", Map.of());

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;"));
        assertTrue(HtmlRenderer.product(EVIL, "/api/v1/products").contains("&lt;script&gt;"));
    }

    @Test
    void htmlEmptyPageAndProblem() {
        assertTrue(HtmlRenderer.page(new ProductPage(List.of(), 0, 20, 0, 0, false, false), "/api/v1/products", Map.of())
                .contains("No products match"));

        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED, "Bad <input>", List.of("name is required"), "/x", "t-9");
        String html = HtmlRenderer.problem(problem);
        assertTrue(html.contains("400 Validation failed"));
        assertTrue(html.contains("Bad &lt;input&gt;"));
        assertTrue(html.contains("<li>name is required</li>"));
        assertTrue(html.contains("t-9"));
    }

    @Test
    void textListIsAnAlignedTable() {
        ProductResponse hub = new ProductResponse(12, "ELEC-HUB-01", "USB-C Hub", null, "Electronics", new BigDecimal("1899.00"), 6, T, T);
        String text = TextRenderer.page(new ProductPage(List.of(MOUSE, hub), 0, 2, 5, 3, true, false));

        String[] lines = text.split("\n");
        assertTrue(lines[0].startsWith("ID  SKU"));
        assertTrue(lines[1].startsWith("--  ---"));
        assertEquals(lines[2].indexOf("799.00") + "799.00".length(), lines[3].indexOf("1899.00") + "1899.00".length(),
                "prices are right-aligned");
        assertTrue(text.contains("5 product(s), page 1 of 3, page size 2 - next: page=1"));
    }

    @Test
    void textProductAndProblem() {
        assertTrue(TextRenderer.product(MOUSE).contains("  price:       799.00"));
        String problem = TextRenderer.problem(
                Problems.of(ErrorCode.PRODUCT_NOT_FOUND, "Product 9 was not found", List.of(), "/p/9", "t-1"));
        assertTrue(problem.startsWith("404 Product not found [PRODUCT_NOT_FOUND]"));
        assertTrue(problem.contains("traceId: t-1"));
        assertTrue(TextRenderer.page(new ProductPage(List.of(), 0, 20, 0, 0, false, false)).contains("(no products)"));
    }

    @Test
    void queryStringsReplaceOneParameterAndEncode() {
        Map<String, List<String>> query = new LinkedHashMap<>();
        query.put("q", List.of("50% off"));
        query.put("page", List.of("3"));
        query.put("empty", List.of(""));

        assertEquals("?q=50%25+off&page=4", QueryStrings.with(query, "page", "4"));
        assertEquals("?q=50%25+off", QueryStrings.with(query, "page", null));
        assertEquals("", QueryStrings.with(Map.of(), "page", null));
        assertEquals("50% off", QueryStrings.first(query, "q"));
        assertEquals("", QueryStrings.first(query, "missing"));
    }
}
