package com.infy.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** A stored product. Immutable; the API layer maps it to its wire representation. */
public record Product(
        Long id,
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        int stock,
        Instant createdAt,
        Instant updatedAt) {
}
