package com.infy.catalog.api;

import com.infy.catalog.api.SearchParams.SearchQuery;
import com.infy.catalog.domain.SortDirection;
import com.infy.catalog.domain.SortField;
import com.infy.catalog.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Query parameter parsing: sort whitelist, numbers, booleans, and "report every error at once". */
class RequestParsingTest {

    @Test
    void sortAcceptsFieldWithOptionalDirection() {
        assertNull(SortParser.parse(null));
        assertNull(SortParser.parse("  "));
        assertEquals(new SortParser.Sort(SortField.PRICE, SortDirection.ASC), SortParser.parse("price"));
        assertEquals(new SortParser.Sort(SortField.PRICE, SortDirection.DESC), SortParser.parse("Price, DESC"));
        assertEquals(new SortParser.Sort(SortField.CREATED_AT, SortDirection.ASC), SortParser.parse("createdAt,asc"));
    }

    @Test
    void sortRejectsUnknownFieldsDirectionsAndSqlLookingInput() {
        assertThrows(IllegalArgumentException.class, () -> SortParser.parse("price;drop table product"));
        assertThrows(IllegalArgumentException.class, () -> SortParser.parse("password"));
        assertThrows(IllegalArgumentException.class, () -> SortParser.parse("price,sideways"));
        assertThrows(IllegalArgumentException.class, () -> SortParser.parse("price,desc,extra"));
    }

    @Test
    void searchParamsParseIntoTypedQuery() {
        SearchQuery query = SearchParams.of("mouse", "Electronics", "100", "999.99", "TRUE", "2", "10", "price,desc")
                .toQuery();

        assertEquals("mouse", query.criteria().keyword());
        assertEquals("Electronics", query.criteria().category());
        assertEquals(new BigDecimal("100"), query.criteria().minPrice());
        assertEquals(new BigDecimal("999.99"), query.criteria().maxPrice());
        assertTrue(query.criteria().inStockOnly());
        assertEquals(Integer.valueOf(2), query.page());
        assertEquals(Integer.valueOf(10), query.size());
        assertEquals(SortField.PRICE, query.sort());
        assertEquals(SortDirection.DESC, query.direction());
    }

    @Test
    void emptySearchParamsMeanNoFiltersAndDefaults() {
        SearchQuery query = SearchParams.of(null, null, "", null, null, null, " ", null).toQuery();

        assertNull(query.criteria().minPrice());
        assertFalse(query.criteria().inStockOnly());
        assertNull(query.page());
        assertNull(query.size());
        assertNull(query.sort());
    }

    @Test
    void invalidSearchParamsAreAllReportedTogether() {
        InvalidRequestException e = assertThrows(InvalidRequestException.class,
                () -> SearchParams.of(null, null, "cheap", "1e", "yes", "first", "ten", "colour").toQuery());

        assertEquals(List.of(
                "minPrice must be a number",
                "maxPrice must be a number",
                "inStock must be true or false",
                "page must be an integer",
                "size must be an integer",
                "sort field must be one of: id, name, price, stock, createdAt"), e.violations());
    }
}
