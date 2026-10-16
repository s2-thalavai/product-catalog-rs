package com.infy.catalog.api;

import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;

import java.util.Locale;
import java.util.Map;

/**
 * Parses {@code sort=field[,asc|desc]} against a whitelist. Client input never reaches SQL:
 * it is mapped to {@link SortField}, which the repositories translate to columns.
 */
public final class SortParser {

    private static final Map<String, SortField> FIELDS = Map.of(
            "id", SortField.ID,
            "name", SortField.NAME,
            "price", SortField.PRICE,
            "stock", SortField.STOCK,
            "createdat", SortField.CREATED_AT);

    static final String ALLOWED = "id, name, price, stock, createdAt";

    private SortParser() {
    }

    public record Sort(SortField field, SortDirection direction) {
    }

    /** Null or blank means "default" (the service sorts by id ascending). */
    public static Sort parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(",", -1);
        if (parts.length > 2) {
            throw new IllegalArgumentException("sort must look like field[,asc|desc]");
        }
        SortField field = FIELDS.get(parts[0].trim().toLowerCase(Locale.ROOT));
        if (field == null) {
            throw new IllegalArgumentException("sort field must be one of: " + ALLOWED);
        }
        SortDirection direction = SortDirection.ASC;
        if (parts.length == 2) {
            switch (parts[1].trim().toLowerCase(Locale.ROOT)) {
                case "asc" -> direction = SortDirection.ASC;
                case "desc" -> direction = SortDirection.DESC;
                default -> throw new IllegalArgumentException("sort direction must be asc or desc");
            }
        }
        return new Sort(field, direction);
    }
}
