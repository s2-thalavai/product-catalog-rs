package com.infy.catalog.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.infy.catalog.Application;
import com.infy.catalog.config.AppConfig;
import com.infy.catalog.infra.LoggingSetup;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract guard: diffs the published OpenAPI document against the committed baseline
 * (src/test/resources/contract/openapi.json). Writes the current one to target/contract/.
 * Intended, backward-compatible change? Run scripts/update-contract.sh and commit the baseline.
 */
class OpenApiContractIT {

    private static final Path OUTPUT = Path.of("target", "contract", "openapi.json");
    private static final Path BASELINE = Path.of("src", "test", "resources", "contract", "openapi.json");
    /** Sorted keys so the comparison does not depend on reflection order. */
    private static final ObjectMapper CANONICAL = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void publishedOpenApiMatchesBaseline() throws Exception {
        LoggingSetup.configure();
        String published;
        try (Application app = Application.start(AppConfig.fromEnvironment(Map.of("APP_PORT", "0", "SHUTDOWN_GRACE_SECONDS", "0")))) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/openapi.json")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            published = canonical(response.body());
        }
        Files.createDirectories(OUTPUT.getParent());
        Files.writeString(OUTPUT, published);

        assumeTrue(Files.exists(BASELINE),
                "No OpenAPI baseline yet: run scripts/update-contract.sh and commit src/test/resources/contract/");
        assertEquals(canonical(Files.readString(BASELINE)), published,
                "The OpenAPI contract changed. If intended and backward compatible, run scripts/update-contract.sh");
    }

    static String canonical(String json) throws Exception {
        JsonNode tree = CANONICAL.readTree(json);
        Object asMap = CANONICAL.treeToValue(tree, Object.class);
        return CANONICAL.writeValueAsString(asMap) + "\n";
    }
}
