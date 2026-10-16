package com.infy.catalog.domain;

import java.math.BigDecimal;

/** Fields to change in a partial update. Null = leave unchanged. */
public record ProductPatch(
        String sku,
        String name,
        String description,
        String category,
        BigDecimal price,
        Integer stock) {
}
