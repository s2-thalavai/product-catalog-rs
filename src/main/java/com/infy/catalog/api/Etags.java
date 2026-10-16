package com.infy.catalog.api;

import com.infy.catalog.domain.Product;

/**
 * Strong entity tag for a product version: changes whenever the product is updated.
 * Used for conditional GET (If-None-Match -> 304) and optimistic concurrency (If-Match -> 412).
 */
public final class Etags {

    private Etags() {
    }

    /** Opaque value without quotes (JAX-RS EntityTag adds them). */
    public static String of(Product product) {
        long version = product.updatedAt().getEpochSecond() * 1_000_000L + product.updatedAt().getNano() / 1_000;
        return Long.toHexString(product.id()) + "-" + Long.toHexString(version);
    }
}
