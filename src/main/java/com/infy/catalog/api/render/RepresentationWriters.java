package com.infy.catalog.api.render;

import com.infy.catalog.api.dto.ProductPage;
import com.infy.catalog.api.dto.ProductResponse;
import com.infy.catalog.api.problem.ProblemDetail;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Custom JAX-RS MessageBodyWriters for the two "human" media types. JSON (Jackson) and XML (JAXB)
 * are handled by standard providers; these turn the same DTOs into HTML and plain text.
 */
public final class RepresentationWriters {

    static final Set<Class<?>> SUPPORTED = Set.of(ProductPage.class, ProductResponse.class, ProblemDetail.class);

    private RepresentationWriters() {
    }

    /** text/html */
    @Provider
    @Produces(MediaType.TEXT_HTML)
    public static class Html implements MessageBodyWriter<Object> {

        @Context
        private UriInfo uriInfo;

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return SUPPORTED.contains(type);
        }

        @Override
        public void writeTo(Object entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap<String, Object> headers, OutputStream out) throws IOException {
            String listPath = "/api/v1/products";
            String html;
            if (entity instanceof ProductPage page) {
                Map<String, List<String>> query = uriInfo.getQueryParameters();
                html = HtmlRenderer.page(page, listPath, query);
            } else if (entity instanceof ProductResponse product) {
                html = HtmlRenderer.product(product, listPath);
            } else {
                html = HtmlRenderer.problem((ProblemDetail) entity);
            }
            write(html, "text/html; charset=UTF-8", headers, out);
        }
    }

    /** text/plain */
    @Provider
    @Produces(MediaType.TEXT_PLAIN)
    public static class Text implements MessageBodyWriter<Object> {

        @Override
        public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
            return SUPPORTED.contains(type);
        }

        @Override
        public void writeTo(Object entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType,
                            MultivaluedMap<String, Object> headers, OutputStream out) throws IOException {
            String text;
            if (entity instanceof ProductPage page) {
                text = TextRenderer.page(page);
            } else if (entity instanceof ProductResponse product) {
                text = TextRenderer.product(product);
            } else {
                text = TextRenderer.problem((ProblemDetail) entity);
            }
            write(text, "text/plain; charset=UTF-8", headers, out);
        }
    }

    private static void write(String body, String contentType, MultivaluedMap<String, Object> headers,
                              OutputStream out) throws IOException {
        headers.putSingle(HttpHeaders.CONTENT_TYPE, contentType);
        out.write(body.getBytes(StandardCharsets.UTF_8));
    }
}
