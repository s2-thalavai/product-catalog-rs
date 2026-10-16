package com.infy.catalog.api;

import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductPatchRequest;
import com.infy.catalog.api.dto.ProductRequest;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.domain.Page;
import com.infy.catalog.domain.Product;
import com.infy.catalog.domain.ProductData;
import com.infy.catalog.domain.ProductPatch;
import com.infy.catalog.domain.exception.InvalidRequestException;

import java.util.List;

/** Converts between API representations and the domain. Keeps the wire format and the domain independent. */
public final class ApiMapper {

    private ApiMapper() {
    }

    public static ProductData toData(ProductRequest request) {
        if (request == null) {
            throw new InvalidRequestException(List.of("request body is required"));
        }
        return new ProductData(request.getSku(), request.getName(), request.getDescription(),
                request.getCategory(), request.getPrice(), request.getStock());
    }

    public static ProductPatch toPatch(ProductPatchRequest request) {
        if (request == null) {
            throw new InvalidRequestException(List.of("request body is required"));
        }
        return new ProductPatch(request.getSku(), request.getName(), request.getDescription(),
                request.getCategory(), request.getPrice(), request.getStock());
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.id(), product.sku(), product.name(), product.description(),
                product.category(), product.price(), product.stock(), product.createdAt(), product.updatedAt());
    }

    public static ProductPage toPage(Page<Product> page) {
        return new ProductPage(page.items().stream().map(ApiMapper::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages(), page.hasNext(), page.hasPrevious());
    }
}
