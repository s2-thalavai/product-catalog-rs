package com.infy.catalog.api;

import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import java.util.Set;

/**
 * Publishes the OpenAPI 3 document generated from the JAX-RS + swagger annotations at
 * GET /openapi.json and /openapi.yaml. Import it into Swagger Editor, Postman, Bruno or Insomnia.
 */
public final class OpenApiSetup {

    private OpenApiSetup() {
    }

    public static OpenApiResource resource() {
        OpenAPI openApi = new OpenAPI().info(new Info()
                .title("Product Catalog API")
                .version("1.0.0")
                .description("CRUD, search and pagination for a product catalog. Every response is available as "
                        + "JSON, XML, HTML or plain text (Accept header or ?format=). Errors are RFC 9457 problem "
                        + "details with a stable errorCode and a traceId."));
        SwaggerConfiguration configuration = new SwaggerConfiguration()
                .openAPI(openApi)
                .prettyPrint(true)
                .resourceClasses(Set.of(ProductResource.class.getName()));
        OpenApiResource resource = new OpenApiResource();
        resource.setOpenApiConfiguration(configuration);
        return resource;
    }
}
