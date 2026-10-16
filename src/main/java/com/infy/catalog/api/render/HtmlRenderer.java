package com.infy.catalog.api.render;

import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.api.problem.ProblemDetail;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side HTML for browsers (text/html). Read-only views: product list with a search form,
 * sorting and pagination links, a product detail page, and error pages.
 * Every dynamic value goes through {@link #escape(String)}; the response also gets a strict CSP.
 */
public final class HtmlRenderer {

    private static final String[][] SORT_OPTIONS = {
            {"", "Id"}, {"name", "Name (A-Z)"}, {"name,desc", "Name (Z-A)"},
            {"price", "Price (low-high)"}, {"price,desc", "Price (high-low)"},
            {"stock,desc", "Stock (high-low)"}, {"createdAt,desc", "Newest"}};

    private static final String STYLE = """
            body{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;margin:0;color:#1f2328;background:#f6f8fa}
            header{background:#24292f;color:#fff;padding:12px 24px}header a{color:#fff;text-decoration:none;font-weight:600}
            main{max-width:1100px;margin:24px auto;padding:0 16px}
            .card{background:#fff;border:1px solid #d0d7de;border-radius:8px;padding:16px;margin-bottom:16px}
            form.search{display:flex;flex-wrap:wrap;gap:8px;align-items:end}
            label{display:flex;flex-direction:column;font-size:12px;color:#57606a;gap:2px}
            input,select{padding:6px 8px;border:1px solid #d0d7de;border-radius:6px;font-size:14px}
            button{padding:7px 14px;border:0;border-radius:6px;background:#1f6feb;color:#fff;font-size:14px;cursor:pointer}
            table{width:100%;border-collapse:collapse;font-size:14px}
            th,td{text-align:left;padding:8px;border-bottom:1px solid #eaeef2}th{color:#57606a;font-weight:600}
            td.num,th.num{text-align:right;font-variant-numeric:tabular-nums}
            .out{color:#cf222e}.muted{color:#57606a}
            nav.pager{display:flex;gap:12px;align-items:center;justify-content:center;margin-top:12px}
            nav.pager a,nav.pager span{padding:4px 10px;border-radius:6px}nav.pager a{border:1px solid #d0d7de;text-decoration:none}
            dl{display:grid;grid-template-columns:140px 1fr;gap:6px 12px}dt{color:#57606a}
            .formats a{margin-right:10px}
            .problem{border-left:4px solid #cf222e}
            """;

    private HtmlRenderer() {
    }

    public static String page(ProductPage page, String listPath, Map<String, List<String>> query) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"card\"><form class=\"search\" method=\"get\" action=\"").append(escape(listPath)).append("\">")
            .append(input("q", "Keyword", query, "text"))
            .append(input("category", "Category", query, "text"))
            .append(input("minPrice", "Min price", query, "number"))
            .append(input("maxPrice", "Max price", query, "number"))
            .append("<label>Sort<select name=\"sort\">");
        String currentSort = QueryStrings.first(query, "sort");
        for (String[] option : SORT_OPTIONS) {
            html.append("<option value=\"").append(escape(option[0])).append('"')
                .append(option[0].equals(currentSort) ? " selected" : "").append('>')
                .append(escape(option[1])).append("</option>");
        }
        html.append("</select></label>")
            .append(input("size", "Per page", query, "number"))
            .append("<label>In stock<input type=\"checkbox\" name=\"inStock\" value=\"true\"")
            .append("true".equals(QueryStrings.first(query, "inStock")) ? " checked" : "").append("></label>")
            .append("<button type=\"submit\">Search</button></form></div>");

        html.append("<div class=\"card\"><p class=\"muted\">").append(page.getTotalElements()).append(" product(s)")
            .append(page.getTotalPages() > 0 ? " - page " + (page.getPage() + 1) + " of " + page.getTotalPages() : "")
            .append("</p>");
        if (page.getItems().isEmpty()) {
            html.append("<p>No products match these filters.</p>");
        } else {
            html.append("<table><thead><tr><th class=\"num\">Id</th><th>SKU</th><th>Name</th><th>Category</th>")
                .append("<th class=\"num\">Price</th><th class=\"num\">Stock</th></tr></thead><tbody>");
            for (ProductResponse p : page.getItems()) {
                html.append("<tr><td class=\"num\">").append(p.getId()).append("</td><td>").append(escape(p.getSku()))
                    .append("</td><td><a href=\"").append(escape(listPath + "/" + p.getId())).append("\">")
                    .append(escape(p.getName())).append("</a></td><td>").append(escape(nullToEmpty(p.getCategory())))
                    .append("</td><td class=\"num\">").append(escape(p.getPrice().toPlainString()))
                    .append("</td><td class=\"num").append(p.getStock() == 0 ? " out" : "").append("\">")
                    .append(p.getStock()).append("</td></tr>");
            }
            html.append("</tbody></table>");
        }
        html.append(pager(page, listPath, query)).append("</div>");
        html.append(formats(listPath + QueryStrings.with(query, "format", null)));
        return document("Products", html.toString());
    }

    public static String product(ProductResponse p, String listPath) {
        String body = "<p><a href=\"" + escape(listPath) + "\">&larr; All products</a></p>"
                + "<div class=\"card\"><h2>" + escape(p.getName()) + "</h2><dl>"
                + row("Id", String.valueOf(p.getId()))
                + row("SKU", p.getSku())
                + row("Description", nullToEmpty(p.getDescription()))
                + row("Category", nullToEmpty(p.getCategory()))
                + row("Price", p.getPrice().toPlainString())
                + row("Stock", String.valueOf(p.getStock()))
                + row("Created", String.valueOf(p.getCreatedAt()))
                + row("Updated", String.valueOf(p.getUpdatedAt()))
                + "</dl></div>"
                + formats(listPath + "/" + p.getId());
        return document(p.getName(), body);
    }

    public static String problem(ProblemDetail problem) {
        StringBuilder body = new StringBuilder("<div class=\"card problem\"><h2>")
                .append(problem.getStatus()).append(' ').append(escape(problem.getTitle())).append("</h2>");
        if (problem.getDetail() != null) {
            body.append("<p>").append(escape(problem.getDetail())).append("</p>");
        }
        if (!problem.getErrors().isEmpty()) {
            body.append("<ul>");
            problem.getErrors().forEach(e -> body.append("<li>").append(escape(e)).append("</li>"));
            body.append("</ul>");
        }
        body.append("<p class=\"muted\">errorCode: ").append(escape(problem.getErrorCode()))
            .append(" &middot; traceId: ").append(escape(nullToEmpty(problem.getTraceId()))).append("</p></div>");
        return document(problem.getStatus() + " " + problem.getTitle(), body.toString());
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String pager(ProductPage page, String listPath, Map<String, List<String>> query) {
        if (page.getTotalPages() <= 1) {
            return "";
        }
        int last = page.getTotalPages() - 1;
        StringBuilder nav = new StringBuilder("<nav class=\"pager\">");
        nav.append(page.hasPrevious() ? link(listPath, query, 0, "&laquo; First") + link(listPath, query, Math.min(page.getPage() - 1, last), "&lsaquo; Prev") : "");
        nav.append("<span>Page ").append(page.getPage() + 1).append(" of ").append(page.getTotalPages()).append("</span>");
        nav.append(page.hasNext() ? link(listPath, query, page.getPage() + 1, "Next &rsaquo;") + link(listPath, query, last, "Last &raquo;") : "");
        return nav.append("</nav>").toString();
    }

    private static String link(String listPath, Map<String, List<String>> query, int pageIndex, String label) {
        return "<a href=\"" + escape(listPath + QueryStrings.with(query, "page", String.valueOf(pageIndex))) + "\">" + label + "</a>";
    }

    private static String formats(String path) {
        String separator = path.contains("?") ? "&" : "?";
        StringBuilder out = new StringBuilder("<p class=\"formats muted\">Also as: ");
        for (String format : new String[] {"json", "xml", "text"}) {
            out.append("<a href=\"").append(escape(path + separator + "format=" + format)).append("\">")
               .append(format.toUpperCase(Locale.ROOT)).append("</a>");
        }
        return out.append("</p>").toString();
    }

    private static String input(String name, String label, Map<String, List<String>> query, String type) {
        return "<label>" + escape(label) + "<input type=\"" + type + "\" name=\"" + name + "\" value=\""
                + escape(QueryStrings.first(query, name)) + "\"" + ("number".equals(type) ? " step=\"any\" min=\"0\"" : "")
                + "></label>";
    }

    private static String row(String label, String value) {
        return "<dt>" + escape(label) + "</dt><dd>" + escape(value) + "</dd>";
    }

    private static String document(String title, String body) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + " - Product Catalog</title><style>" + STYLE + "</style></head>"
                + "<body><header><a href=\"/api/v1/products\">Product Catalog</a></header><main>"
                + body + "</main></body></html>";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
