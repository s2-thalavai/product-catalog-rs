package com.infy.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infy.catalog.Application;
import com.infy.catalog.config.AppConfig;
import com.infy.catalog.infra.LoggingSetup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Black-box tests over real HTTP: starts the application on a random port (in-memory store)
 * and checks status codes, headers, content negotiation and error bodies.
 * Tests share one server, so each uses unique SKUs and its own category.
 */
class ProductApiIT {

    private static final String PRODUCTS = "/api/v1/products";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private static Application app;
    private static String base;

    @BeforeAll
    static void start() {
        LoggingSetup.configure();
        app = Application.start(AppConfig.fromEnvironment(Map.of(
                "APP_PORT", "0",
                "SHUTDOWN_GRACE_SECONDS", "0",
                "PAGE_SIZE_MAX", "50",
                "CORS_ALLOWED_ORIGINS", "https://shop.example.com")));
        base = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stop() {
        if (app != null) {
            app.close();
        }
    }

    // ------------------------------------------------------------------ CRUD (JSON)

    @Test
    void crudLifecycleInJson() throws Exception {
        String sku = sku();
        HttpResponse<String> created = send("POST", PRODUCTS, "application/json", json(sku, "Desk Lamp", "it-crud", "1299.5", 10), "application/json");
        assertEquals(201, created.statusCode(), created.body());
        String location = created.headers().firstValue("Location").orElseThrow();
        assertTrue(location.matches("http://localhost:\\d+/api/v1/products/\\d+"), location);
        assertTrue(created.headers().firstValue("ETag").isPresent());
        JsonNode body = JSON.readTree(created.body());
        assertEquals(sku, body.get("sku").asText());
        assertEquals("1299.50", body.get("price").decimalValue().toPlainString());
        String path = URI.create(location).getPath();

        HttpResponse<String> read = send("GET", path, null, null, "application/json");
        assertEquals(200, read.statusCode());
        assertEquals("Desk Lamp", JSON.readTree(read.body()).get("name").asText());

        HttpResponse<String> replaced = send("PUT", path, "application/json", json(sku, "Desk Lamp v2", "it-crud", "999", 4), "application/json");
        assertEquals(200, replaced.statusCode(), replaced.body());
        assertEquals("999.00", JSON.readTree(replaced.body()).get("price").decimalValue().toPlainString());

        HttpResponse<String> patched = send("PATCH", path, "application/merge-patch+json", "{\"stock\":0}", "application/json");
        assertEquals(200, patched.statusCode(), patched.body());
        JsonNode patchedBody = JSON.readTree(patched.body());
        assertEquals(0, patchedBody.get("stock").asInt());
        assertEquals("Desk Lamp v2", patchedBody.get("name").asText());

        assertEquals(204, send("DELETE", path, null, null, null).statusCode());
        HttpResponse<String> gone = send("GET", path, null, null, "application/json");
        assertEquals(404, gone.statusCode());
        assertEquals("PRODUCT_NOT_FOUND", JSON.readTree(gone.body()).get("errorCode").asText());
    }

    @Test
    void createAndReadInXml() throws Exception {
        String sku = sku();
        String xml = "<product><sku>" + sku + "</sku><name>Xml Kettle</name><category>it-xml</category>"
                + "<price>1499.00</price><stock>3</stock></product>";
        HttpResponse<String> created = send("POST", PRODUCTS, "application/xml", xml, "application/xml");
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.headers().firstValue("Content-Type").orElseThrow().startsWith("application/xml"));
        assertTrue(created.body().contains("<sku>" + sku + "</sku>"), created.body());
        assertTrue(created.body().contains("<price>1499.00</price>"));
    }

    // ------------------------------------------------------------------ content negotiation

    @Test
    void sameResourceInFourRepresentations() throws Exception {
        String path = createProduct(sku(), "Negotiated Mug", "it-conneg", "249.00", 5);

        assertMediaType(send("GET", path, null, null, "application/json"), "application/json", "\"name\":\"Negotiated Mug\"");
        assertMediaType(send("GET", path, null, null, "application/xml"), "application/xml", "<name>Negotiated Mug</name>");
        assertMediaType(send("GET", path, null, null, "text/html"), "text/html", "<h2>Negotiated Mug</h2>");
        assertMediaType(send("GET", path, null, null, "text/plain"), "text/plain", "name:        Negotiated Mug");
        assertMediaType(send("GET", path + "?format=xml", null, null, "application/json"), "application/xml", "<product>");
    }

    @Test
    void defaultsToJsonForWildcardAndHtmlForBrowsers() throws Exception {
        assertMediaType(send("GET", PRODUCTS, null, null, "*/*"), "application/json", "\"items\"");
        HttpResponse<String> browser = send("GET", PRODUCTS, null, null,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        assertMediaType(browser, "text/html", "<form class=\"search\"");
        assertTrue(browser.headers().firstValue("Content-Security-Policy").orElseThrow().contains("default-src 'none'"));
    }

    @Test
    void unsupportedMediaTypesGive406And415AsProblems() throws Exception {
        HttpResponse<String> notAcceptable = send("GET", PRODUCTS, null, null, "image/png");
        assertEquals(406, notAcceptable.statusCode());
        assertProblem(notAcceptable, "NOT_ACCEPTABLE");

        HttpResponse<String> badFormat = send("GET", PRODUCTS + "?format=pdf", null, null, null);
        assertEquals(406, badFormat.statusCode());

        HttpResponse<String> unsupported = send("POST", PRODUCTS, "text/csv", "a,b", "application/json");
        assertEquals(415, unsupported.statusCode());
        assertProblem(unsupported, "UNSUPPORTED_MEDIA_TYPE");
    }

    // ------------------------------------------------------------------ search and pagination

    @Test
    void searchFiltersSortsPaginatesAndSendsLinkHeaders() throws Exception {
        String category = "it-search-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 1; i <= 7; i++) {
            createProduct(sku(), "Search Item " + i, category, String.valueOf(100 * i), i % 3 == 0 ? 0 : 5);
        }
        String query = PRODUCTS + "?category=" + category + "&minPrice=200&inStock=true&sort=price,desc&size=2";

        HttpResponse<String> first = send("GET", query, null, null, "application/json");
        assertEquals(200, first.statusCode(), first.body());
        JsonNode page = JSON.readTree(first.body());
        assertEquals(4, page.get("totalElements").asInt());       // 700, 500, 400, 200
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals(List.of("Search Item 7", "Search Item 5"), names(page));
        assertTrue(page.get("hasNext").asBoolean());
        assertEquals("4", first.headers().firstValue("X-Total-Count").orElseThrow());
        String links = String.join(",", first.headers().allValues("Link"));
        assertTrue(links.contains("rel=\"next\""), links);
        assertTrue(links.contains("page=1"), links);
        assertFalse(links.contains("rel=\"prev\""), links);

        HttpResponse<String> second = send("GET", query + "&page=1", null, null, "application/json");
        assertEquals(List.of("Search Item 4", "Search Item 2"), names(JSON.readTree(second.body())));
        assertFalse(JSON.readTree(second.body()).get("hasNext").asBoolean());
    }

    @Test
    void pageSizeIsCappedAndBadParametersAreAllReported() throws Exception {
        HttpResponse<String> capped = send("GET", PRODUCTS + "?size=5000", null, null, "application/json");
        assertEquals(50, JSON.readTree(capped.body()).get("size").asInt());

        HttpResponse<String> bad = send("GET", PRODUCTS + "?minPrice=cheap&page=first&sort=colour", null, null, "application/json");
        assertEquals(400, bad.statusCode());
        JsonNode problem = assertProblem(bad, "VALIDATION_FAILED");
        assertEquals(3, problem.get("errors").size(), problem.toString());
    }

    // ------------------------------------------------------------------ errors

    @Test
    void validationAndDuplicateSkuProblems() throws Exception {
        HttpResponse<String> invalid = send("POST", PRODUCTS, "application/json",
                "{\"sku\":\"x\",\"name\":\"\",\"price\":-1,\"stock\":-5}", "application/json");
        assertEquals(400, invalid.statusCode());
        JsonNode problem = assertProblem(invalid, "VALIDATION_FAILED");
        assertEquals(4, problem.get("errors").size());
        assertEquals("https://catalog.infy.com/problems/validation-failed", problem.get("type").asText());

        String sku = sku();
        createProduct(sku, "First", "it-dup", "1.00", 1);
        HttpResponse<String> duplicate = send("POST", PRODUCTS, "application/json", json(sku, "Second", "it-dup", "1.00", 1), "application/json");
        assertEquals(409, duplicate.statusCode());
        assertProblem(duplicate, "DUPLICATE_SKU");
    }

    @Test
    void malformedBodiesGive400WithoutInternals() throws Exception {
        HttpResponse<String> brokenJson = send("POST", PRODUCTS, "application/json", "{\"sku\": ", "application/json");
        assertEquals(400, brokenJson.statusCode());
        assertProblem(brokenJson, "MALFORMED_REQUEST");
        assertFalse(brokenJson.body().contains("com.fasterxml"), brokenJson.body());

        HttpResponse<String> wrongType = send("POST", PRODUCTS, "application/json",
                "{\"sku\":\"SKU-T\",\"name\":\"T\",\"price\":\"abc\",\"stock\":1}", "application/json");
        assertEquals(400, wrongType.statusCode());
        assertFalse(wrongType.body().contains("java.math"), wrongType.body());

        HttpResponse<String> brokenXml = send("POST", PRODUCTS, "application/xml", "<product><sku>", "application/json");
        assertEquals(400, brokenXml.statusCode());
    }

    @Test
    void problemsAreNegotiatedToo() throws Exception {
        String missing = PRODUCTS + "/987654321";
        assertMediaType(send("GET", missing, null, null, "application/json"), "application/problem+json", "\"errorCode\":\"PRODUCT_NOT_FOUND\"");
        assertMediaType(send("GET", missing, null, null, "application/xml"), "application/problem+xml", "urn:ietf:rfc:7807");
        assertMediaType(send("GET", missing, null, null, "text/html"), "text/html", "404 Product not found");
        assertMediaType(send("GET", missing, null, null, "text/plain"), "text/plain", "[PRODUCT_NOT_FOUND]");
        assertEquals(404, send("GET", "/api/v1/nothing-here", null, null, "application/json").statusCode());
        HttpResponse<String> wrongMethod = send("DELETE", PRODUCTS, null, null, "application/json");
        assertEquals(405, wrongMethod.statusCode());
        assertTrue(wrongMethod.headers().firstValue("Allow").isPresent());
    }

    // ------------------------------------------------------------------ HTTP caching and concurrency

    @Test
    void conditionalGetAndOptimisticLocking() throws Exception {
        String path = createProduct(sku(), "Etag Lamp", "it-etag", "10.00", 1);
        HttpResponse<String> read = send("GET", path, null, null, "application/json");
        String etag = read.headers().firstValue("ETag").orElseThrow();

        HttpResponse<String> notModified = send("GET", path, null, null, "application/json", Map.of("If-None-Match", etag));
        assertEquals(304, notModified.statusCode());

        HttpResponse<String> stale = send("PATCH", path, "application/json", "{\"stock\":2}", "application/json",
                Map.of("If-Match", "\"stale-version\""));
        assertEquals(412, stale.statusCode());
        assertProblem(stale, "PRECONDITION_FAILED");

        HttpResponse<String> fresh = send("PATCH", path, "application/json", "{\"stock\":2}", "application/json",
                Map.of("If-Match", etag));
        assertEquals(200, fresh.statusCode(), fresh.body());
        assertFalse(etag.equals(fresh.headers().firstValue("ETag").orElseThrow()), "ETag changes after an update");
    }

    // ------------------------------------------------------------------ cross-cutting

    @Test
    void correlationIdIsEchoedAndUsedAsTraceId() throws Exception {
        HttpResponse<String> response = send("GET", PRODUCTS + "/987654321", null, null, "application/json",
                Map.of("X-Correlation-Id", "it-trace-1"));
        assertEquals("it-trace-1", response.headers().firstValue("X-Correlation-Id").orElseThrow());
        assertEquals("it-trace-1", JSON.readTree(response.body()).get("traceId").asText());
    }

    @Test
    void corsOnlyForTheConfiguredOrigin() throws Exception {
        HttpResponse<String> allowed = send("GET", PRODUCTS, null, null, "application/json", Map.of("Origin", "https://shop.example.com"));
        assertEquals("https://shop.example.com", allowed.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());

        HttpResponse<String> other = send("GET", PRODUCTS, null, null, "application/json", Map.of("Origin", "https://evil.example.com"));
        assertTrue(other.headers().firstValue("Access-Control-Allow-Origin").isEmpty());

        HttpResponse<String> preflight = send("OPTIONS", PRODUCTS, null, null, null, Map.of(
                "Origin", "https://shop.example.com", "Access-Control-Request-Method", "PATCH"));
        assertTrue(preflight.statusCode() < 300, "preflight status " + preflight.statusCode());
        assertTrue(preflight.headers().firstValue("Access-Control-Allow-Methods").orElseThrow().contains("PATCH"));
    }

    @Test
    void healthOpenApiAndRoot() throws Exception {
        assertEquals(200, send("GET", "/health/live", null, null, null).statusCode());
        assertEquals(200, send("GET", "/health/ready", null, null, null).statusCode());
        HttpResponse<String> openApi = send("GET", "/openapi.json", null, null, null);
        assertEquals(200, openApi.statusCode());
        assertTrue(openApi.body().contains("/api/v1/products/{id}"), openApi.body());
        assertEquals(303, send("GET", "/", null, null, null).statusCode());
    }

    // ------------------------------------------------------------------ helpers

    private static String createProduct(String sku, String name, String category, String price, int stock) throws Exception {
        HttpResponse<String> response = send("POST", PRODUCTS, "application/json", json(sku, name, category, price, stock), "application/json");
        assertEquals(201, response.statusCode(), response.body());
        return URI.create(response.headers().firstValue("Location").orElseThrow()).getPath();
    }

    private static String json(String sku, String name, String category, String price, int stock) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"" + name + "\",\"category\":\"" + category
                + "\",\"price\":" + price + ",\"stock\":" + stock + "}";
    }

    private static HttpResponse<String> send(String method, String path, String contentType, String body, String accept)
            throws Exception {
        return send(method, path, contentType, body, accept, Map.of());
    }

    private static HttpResponse<String> send(String method, String path, String contentType, String body, String accept,
                                             Map<String, String> headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        if (accept != null) {
            request.header("Accept", accept);
        }
        headers.forEach(request::header);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertMediaType(HttpResponse<String> response, String mediaType, String fragment) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith(mediaType), "Content-Type was " + contentType);
        assertTrue(response.body().contains(fragment), response.body());
    }

    private static JsonNode assertProblem(HttpResponse<String> response, String errorCode) throws Exception {
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"),
                "Content-Type was " + response.headers().firstValue("Content-Type").orElse(""));
        JsonNode problem = JSON.readTree(response.body());
        assertEquals(errorCode, problem.get("errorCode").asText(), response.body());
        assertEquals(response.statusCode(), problem.get("status").asInt());
        assertNotNull(problem.get("traceId"));
        return problem;
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("items").forEach(item -> names.add(item.get("name").asText()));
        return names;
    }

    private static String sku() {
        return "IT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(java.util.Locale.ROOT);
    }
}
