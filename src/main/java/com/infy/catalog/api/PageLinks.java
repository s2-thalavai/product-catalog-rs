package com.infy.catalog.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Which pagination links (RFC 8288 Link header relations) a page should advertise. */
public final class PageLinks {

    private PageLinks() {
    }

    /**
     * @return relation -> page index, in the order first, prev, next, last.
     *         "prev" is omitted on the first page, "next" on the last; a page past the end
     *         links back to the last real page.
     */
    public static Map<String, Integer> relations(int page, int totalPages) {
        Map<String, Integer> links = new LinkedHashMap<>();
        int last = Math.max(totalPages - 1, 0);
        links.put("first", 0);
        if (page > 0) {
            links.put("prev", Math.min(page - 1, last));
        }
        if (page < last) {
            links.put("next", page + 1);
        }
        links.put("last", last);
        return links;
    }
}
