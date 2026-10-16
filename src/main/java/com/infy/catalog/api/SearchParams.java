package com.infy.catalog.api;

import com.infy.catalog.domain.ProductSearchCriteria;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.InvalidRequestException;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.ws.rs.QueryParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Query parameters of GET /api/v1/products, bound with @BeanParam.
 * Values are taken as strings and parsed here so that bad input becomes a 400 problem
 * listing every invalid parameter (JAX-RS would otherwise answer 404 for an unparsable query param).
 */
public class SearchParams {

    @QueryParam("q")
    @Parameter(description = "Keyword, case-insensitive, matched against name, SKU and description")
    private String q;

    @QueryParam("category")
    @Parameter(description = "Category, case-insensitive exact match")
    private String category;

    @QueryParam("minPrice")
    @Parameter(description = "Minimum price (inclusive)")
    private String minPrice;

    @QueryParam("maxPrice")
    @Parameter(description = "Maximum price (inclusive)")
    private String maxPrice;

    @QueryParam("inStock")
    @Parameter(description = "true = only products with stock > 0")
    private String inStock;

    @QueryParam("page")
    @Parameter(description = "0-based page index (default 0)")
    private String page;

    @QueryParam("size")
    @Parameter(description = "Page size (default 20, capped at 100)")
    private String size;

    @QueryParam("sort")
    @Parameter(description = "field[,asc|desc]; field = id, name, price, stock, createdAt", example = "price,desc")
    private String sort;

    public SearchParams() {
        // populated by JAX-RS
    }

    public static SearchParams of(String q, String category, String minPrice, String maxPrice, String inStock,
                                  String page, String size, String sort) {
        SearchParams params = new SearchParams();
        params.q = q;
        params.category = category;
        params.minPrice = minPrice;
        params.maxPrice = maxPrice;
        params.inStock = inStock;
        params.page = page;
        params.size = size;
        params.sort = sort;
        return params;
    }

    /** The parsed query. Throws {@link InvalidRequestException} listing every invalid parameter. */
    public SearchQuery toQuery() {
        List<String> errors = new ArrayList<>();
        BigDecimal min = decimal("minPrice", minPrice, errors);
        BigDecimal max = decimal("maxPrice", maxPrice, errors);
        boolean onlyInStock = bool("inStock", inStock, errors);
        Integer pageIndex = integer("page", page, errors);
        Integer pageSize = integer("size", size, errors);
        SortParser.Sort parsedSort = null;
        try {
            parsedSort = SortParser.parse(sort);
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }
        if (!errors.isEmpty()) {
            throw new InvalidRequestException(errors);
        }
        return new SearchQuery(
                new ProductSearchCriteria(q, category, min, max, onlyInStock),
                pageIndex,
                pageSize,
                parsedSort == null ? null : parsedSort.field(),
                parsedSort == null ? null : parsedSort.direction());
    }

    /** Parsed, typed search request handed to ProductService (which applies defaults, caps and range checks). */
    public record SearchQuery(ProductSearchCriteria criteria, Integer page, Integer size,
                              SortField sort, SortDirection direction) {
    }

    private static BigDecimal decimal(String name, String value, List<String> errors) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            errors.add(name + " must be a number");
            return null;
        }
    }

    private static Integer integer(String name, String value, List<String> errors) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            errors.add(name + " must be an integer");
            return null;
        }
    }

    private static boolean bool(String name, String value, List<String> errors) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("false")) {
            return normalized.equals("true");
        }
        errors.add(name + " must be true or false");
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
