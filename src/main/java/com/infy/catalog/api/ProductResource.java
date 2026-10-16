package com.infy.catalog.api;

import com.infy.catalog.api.SearchParams.SearchQuery;
import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductPatchRequest;
import com.infy.catalog.api.dto.ProductRequest;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.api.problem.ProblemDetail;
import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.Product;
import com.infy.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.Map;
import java.util.Objects;

/**
 * Product catalog resource.
 *
 * <pre>
 *   GET    /api/v1/products            search + pagination (q, category, minPrice, maxPrice, inStock, page, size, sort)
 *   POST   /api/v1/products            create                -> 201 + Location + ETag
 *   GET    /api/v1/products/{id}       read                  -> ETag; If-None-Match -> 304
 *   PUT    /api/v1/products/{id}       full replace          -> If-Match optional (412 on mismatch)
 *   PATCH  /api/v1/products/{id}       partial update        -> If-Match optional
 *   DELETE /api/v1/products/{id}       delete                -> 204
 * </pre>
 *
 * Every response is available as JSON, XML, HTML or plain text (Accept header or ?format=).
 * The qs values make JSON the default for clients that accept anything, while browsers
 * (which prefer text/html) get HTML.
 */
@Path("api/v1/products")
@Tag(name = "Products", description = "Product catalog: CRUD, search and pagination")
@Produces({
        MediaType.APPLICATION_JSON + ";qs=1",
        MediaType.APPLICATION_XML + ";qs=0.9",
        MediaType.TEXT_XML + ";qs=0.85",
        MediaType.TEXT_HTML + ";qs=0.8",
        MediaType.TEXT_PLAIN + ";qs=0.7"})
public class ProductResource {

    static final String MERGE_PATCH_JSON = "application/merge-patch+json";
    private static final String ID = "{id: [0-9]+}";

    private final ProductService service;

    public ProductResource(ProductService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @GET
    @Operation(summary = "Search products (paginated)",
            description = "All filters are optional and combined with AND. Pagination links are returned in the "
                    + "Link header (first, prev, next, last) and the total in X-Total-Count.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "A page of products",
                            content = @Content(schema = @Schema(implementation = ProductPage.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid query parameter",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))})
    public Response search(@BeanParam SearchParams params, @Context UriInfo uriInfo) {
        SearchQuery query = params.toQuery();
        Page<Product> page = service.search(query.criteria(), query.page(), query.size(), query.sort(), query.direction());

        Response.ResponseBuilder response = Response.ok(ApiMapper.toPage(page))
                .header("X-Total-Count", page.totalElements())
                .cacheControl(noCache());
        for (Map.Entry<String, Integer> link : PageLinks.relations(page.page(), page.totalPages()).entrySet()) {
            URI uri = uriInfo.getRequestUriBuilder()
                    .replaceQueryParam("page", link.getValue())
                    .replaceQueryParam("size", page.size())
                    .build();
            response.link(uri, link.getKey());
        }
        return response.build();
    }

    @GET
    @Path(ID)
    @Operation(summary = "Get a product",
            description = "Returns an ETag. Send it back in If-None-Match to get 304 Not Modified when unchanged.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The product",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "304", description = "Not modified (If-None-Match matched)"),
                    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))})
    public Response get(@PathParam("id") long id, @Context Request request) {
        Product product = service.get(id);
        EntityTag etag = etag(product);
        Response.ResponseBuilder notModified = request.evaluatePreconditions(etag);
        if (notModified != null) {
            Response conditional = notModified.tag(etag).cacheControl(noCache()).build();
            if (conditional.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode()) {
                return conditional;
            }
            throw new WebApplicationException(conditional);
        }
        return Response.ok(ApiMapper.toResponse(product)).tag(etag).cacheControl(noCache()).build();
    }

    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Operation(summary = "Create a product",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Created; Location points to the new product",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or MALFORMED_REQUEST",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class))),
                    @ApiResponse(responseCode = "409", description = "DUPLICATE_SKU",
                            content = @Content(mediaType = "application/problem+json",
                                    schema = @Schema(implementation = ProblemDetail.class)))})
    public Response create(ProductRequest body, @Context UriInfo uriInfo) {
        Product created = service.create(ApiMapper.toData(body));
        URI location = uriInfo.getAbsolutePathBuilder().path(String.valueOf(created.id())).build();
        return Response.created(location).tag(etag(created)).entity(ApiMapper.toResponse(created)).build();
    }

    @PUT
    @Path(ID)
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Operation(summary = "Replace a product (all writable fields)",
            description = "Optional optimistic locking: send If-Match with the ETag you read; 412 if it changed meanwhile.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The updated product",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED"),
                    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
                    @ApiResponse(responseCode = "409", description = "DUPLICATE_SKU"),
                    @ApiResponse(responseCode = "412", description = "PRECONDITION_FAILED (ETag mismatch)")})
    public Response replace(@PathParam("id") long id, ProductRequest body, @Context Request request) {
        checkPreconditions(request, service.get(id));
        Product updated = service.update(id, ApiMapper.toData(body));
        return Response.ok(ApiMapper.toResponse(updated)).tag(etag(updated)).build();
    }

    @PATCH
    @Path(ID)
    @Consumes({MERGE_PATCH_JSON, MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.TEXT_XML})
    @Operation(summary = "Partially update a product",
            description = "Send only the fields to change (JSON Merge Patch style). \"\" clears description or category.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The updated product",
                            content = @Content(schema = @Schema(implementation = ProductResponse.class))),
                    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED"),
                    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
                    @ApiResponse(responseCode = "409", description = "DUPLICATE_SKU"),
                    @ApiResponse(responseCode = "412", description = "PRECONDITION_FAILED (ETag mismatch)")})
    public Response patch(@PathParam("id") long id, ProductPatchRequest body, @Context Request request) {
        checkPreconditions(request, service.get(id));
        Product updated = service.patch(id, ApiMapper.toPatch(body));
        return Response.ok(ApiMapper.toResponse(updated)).tag(etag(updated)).build();
    }

    @DELETE
    @Path(ID)
    @Operation(summary = "Delete a product",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted"),
                    @ApiResponse(responseCode = "404", description = "PRODUCT_NOT_FOUND"),
                    @ApiResponse(responseCode = "412", description = "PRECONDITION_FAILED (ETag mismatch)")})
    public Response delete(@PathParam("id") long id, @Context Request request) {
        checkPreconditions(request, service.get(id));
        service.delete(id);
        return Response.noContent().build();
    }

    /**
     * If-Match / If-Unmodified-Since handling. Note: check-then-write is not atomic; for strict
     * optimistic locking add a version column and check it in the UPDATE statement.
     */
    private static void checkPreconditions(Request request, Product current) {
        Response.ResponseBuilder failed = request.evaluatePreconditions(etag(current));
        if (failed != null) {
            throw new WebApplicationException(failed.build());
        }
    }

    private static EntityTag etag(Product product) {
        return new EntityTag(Etags.of(product));
    }

    private static CacheControl noCache() {
        CacheControl cacheControl = new CacheControl();
        cacheControl.setNoCache(true);
        cacheControl.setPrivate(true);
        return cacheControl;
    }
}
