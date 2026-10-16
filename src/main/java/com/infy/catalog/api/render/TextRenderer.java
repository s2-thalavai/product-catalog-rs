package com.infy.catalog.api.render;

import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.api.problem.ProblemDetail;

import java.util.ArrayList;
import java.util.List;

/** Human-readable text/plain output, handy in a terminal: {@code curl -H 'Accept: text/plain' ...}. */
public final class TextRenderer {

    private static final String[] HEADERS = {"ID", "SKU", "NAME", "CATEGORY", "PRICE", "STOCK"};
    private static final boolean[] RIGHT_ALIGNED = {true, false, false, false, true, true};

    private TextRenderer() {
    }

    public static String page(ProductPage page) {
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADERS);
        for (ProductResponse p : page.getItems()) {
            rows.add(new String[] {String.valueOf(p.getId()), p.getSku(), p.getName(), nullToEmpty(p.getCategory()),
                    p.getPrice().toPlainString(), String.valueOf(p.getStock())});
        }
        int[] widths = new int[HEADERS.length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        StringBuilder out = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            out.append(line(rows.get(r), widths)).append('\n');
            if (r == 0) {
                String[] rule = new String[widths.length];
                for (int i = 0; i < widths.length; i++) {
                    rule[i] = "-".repeat(widths[i]);
                }
                out.append(line(rule, widths)).append('\n');
            }
        }
        if (page.getItems().isEmpty()) {
            out.append("(no products)\n");
        }
        out.append('\n').append(page.getTotalElements()).append(" product(s)");
        if (page.getTotalPages() > 0) {
            out.append(", page ").append(page.getPage() + 1).append(" of ").append(page.getTotalPages());
        }
        out.append(", page size ").append(page.getSize());
        if (page.hasNext()) {
            out.append(" - next: page=").append(page.getPage() + 1);
        }
        return out.append('\n').toString();
    }

    public static String product(ProductResponse p) {
        return "Product " + p.getId() + "\n"
                + "  sku:         " + p.getSku() + "\n"
                + "  name:        " + p.getName() + "\n"
                + "  description: " + nullToEmpty(p.getDescription()) + "\n"
                + "  category:    " + nullToEmpty(p.getCategory()) + "\n"
                + "  price:       " + p.getPrice().toPlainString() + "\n"
                + "  stock:       " + p.getStock() + "\n"
                + "  created:     " + p.getCreatedAt() + "\n"
                + "  updated:     " + p.getUpdatedAt() + "\n";
    }

    public static String problem(ProblemDetail problem) {
        StringBuilder out = new StringBuilder()
                .append(problem.getStatus()).append(' ').append(problem.getTitle())
                .append(" [").append(problem.getErrorCode()).append("]\n");
        if (problem.getDetail() != null) {
            out.append(problem.getDetail()).append('\n');
        }
        problem.getErrors().forEach(e -> out.append("  - ").append(e).append('\n'));
        return out.append("traceId: ").append(nullToEmpty(problem.getTraceId())).append('\n').toString();
    }

    private static String line(String[] cells, int[] widths) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append("  ");
            }
            String padding = " ".repeat(widths[i] - cells[i].length());
            out.append(RIGHT_ALIGNED[i] ? padding + cells[i] : cells[i] + padding);
        }
        return out.toString().stripTrailing();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
