package com.infy.catalog;

import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.infy.catalog.api.HealthResource;
import com.infy.catalog.api.JsonMapperFactory;
import com.infy.catalog.api.OpenApiSetup;
import com.infy.catalog.api.ProductResource;
import com.infy.catalog.api.filter.CorrelationIdFilter;
import com.infy.catalog.api.filter.CorsFilter;
import com.infy.catalog.api.filter.CorsPolicy;
import com.infy.catalog.api.filter.FormatParameterFilter;
import com.infy.catalog.api.filter.SecurityHeadersFilter;
import com.infy.catalog.api.mapper.CatalogExceptionMapper;
import com.infy.catalog.api.mapper.JsonProcessingExceptionMapper;
import com.infy.catalog.api.mapper.UnhandledExceptionMapper;
import com.infy.catalog.api.mapper.WebApplicationExceptionMapper;
import com.infy.catalog.api.render.RepresentationWriters;
import com.infy.catalog.config.AppConfig;
import com.infy.catalog.config.AppConfig.StoreType;
import com.infy.catalog.infra.DataSourceFactory;
import com.infy.catalog.infra.DatabaseMigrations;
import com.infy.catalog.infra.LoggingSetup;
import com.infy.catalog.repository.InMemoryProductRepository;
import com.infy.catalog.repository.JdbcProductRepository;
import com.infy.catalog.repository.ProductRepository;
import com.infy.catalog.repository.SeedData;
import com.infy.catalog.service.PagingPolicy;
import com.infy.catalog.service.ProductService;
import com.zaxxer.hikari.HikariDataSource;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Composition root: wires config, storage, service and the JAX-RS application, and serves it on
 * an embedded Grizzly HTTP server (no servlet container). Owns graceful shutdown.
 *
 * <pre>
 *   /api/v1/products[/{id}]   REST API (JSON, XML, HTML, text)
 *   /openapi.json|yaml        OpenAPI 3 document
 *   /health/live, /ready      probes
 * </pre>
 */
public final class Application implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Application.class);
    private static final String LISTENER = "grizzly";

    private final AppConfig config;
    private final HikariDataSource dataSource;
    private final HttpServer server;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public static void main(String[] args) {
        LoggingSetup.configure();
        try {
            AppConfig config = AppConfig.fromEnvironment(System.getenv());
            log.info("Starting Product Catalog REST API with {}", config);
            Application app = start(config);
            Runtime.getRuntime().addShutdownHook(new Thread(app::close, "graceful-shutdown"));
        } catch (RuntimeException e) {
            log.error("Startup failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    /** Starts the service. Port 0 picks a free port (used by tests); see {@link #port()}. */
    public static Application start(AppConfig config) {
        HikariDataSource dataSource = null;
        try {
            ProductRepository repository;
            if (config.storeType() == StoreType.POSTGRES) {
                dataSource = DataSourceFactory.create(config);
                DatabaseMigrations.migrate(dataSource, config.seedData());
                repository = new JdbcProductRepository(dataSource);
            } else {
                log.warn("STORE_TYPE=memory: data is lost on restart and not shared between replicas");
                repository = new InMemoryProductRepository();
                if (config.seedData()) {
                    SeedData.PRODUCTS.forEach(repository::insert);
                }
            }
            ProductService service = new ProductService(repository,
                    new PagingPolicy(config.defaultPageSize(), config.maxPageSize()));
            return new Application(config, service, dataSource);
        } catch (IOException e) {
            closeQuietly(dataSource);
            throw new UncheckedIOException("Could not start HTTP server on port " + config.port(), e);
        } catch (RuntimeException e) {
            closeQuietly(dataSource);
            throw e;
        }
    }

    private Application(AppConfig config, ProductService service, HikariDataSource dataSource) throws IOException {
        this.config = config;
        this.dataSource = dataSource;

        ResourceConfig resources = resourceConfig(service, config, this::isReady);
        this.server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create("http://0.0.0.0:" + config.port() + "/"), resources, false);
        NetworkListener listener = server.getListener(LISTENER);
        listener.getTransport().setWorkerThreadPoolConfig(ThreadPoolConfig.defaultConfig()
                .setPoolName("http-worker")
                .setCorePoolSize(config.httpThreads())
                .setMaxPoolSize(config.httpThreads()));
        server.start();
        ready.set(true);
        log.info("Listening on port {} - try http://localhost:{}/api/v1/products and /openapi.json", port(), port());
    }

    /** Everything Jersey serves. Providers are registered explicitly: no classpath scanning surprises. */
    static ResourceConfig resourceConfig(ProductService service, AppConfig config, java.util.function.BooleanSupplier ready) {
        return new ResourceConfig()
                .register(new ProductResource(service))
                .register(new HealthResource(ready))
                .register(OpenApiSetup.resource())
                // representations
                .register(new JacksonJsonProvider(JsonMapperFactory.create()))
                .register(RepresentationWriters.Html.class)
                .register(RepresentationWriters.Text.class)
                // filters
                .register(new CorrelationIdFilter())
                .register(new FormatParameterFilter())
                .register(new CorsFilter(new CorsPolicy(config.corsAllowedOrigins())))
                .register(new SecurityHeadersFilter())
                // errors -> RFC 9457 problem details
                .register(CatalogExceptionMapper.class)
                .register(WebApplicationExceptionMapper.class)
                .register(JsonProcessingExceptionMapper.class)
                .register(UnhandledExceptionMapper.class)
                .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                // only the providers registered above (no META-INF/services discovery of a second Jackson provider)
                .property(ServerProperties.METAINF_SERVICES_LOOKUP_DISABLE, true);
    }

    public int port() {
        return server.getListener(LISTENER).getPort();
    }

    public boolean isReady() {
        return ready.get() && databaseAnswers();
    }

    /**
     * Graceful shutdown (SIGTERM from Kubernetes): report not-ready, stop accepting connections,
     * let in-flight requests finish for up to SHUTDOWN_GRACE_SECONDS, then release resources.
     */
    @Override
    public void close() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        log.info("Shutting down, waiting up to {}s for in-flight requests", config.shutdownGraceSeconds());
        ready.set(false);
        try {
            server.shutdown(config.shutdownGraceSeconds(), TimeUnit.SECONDS)
                    .get(config.shutdownGraceSeconds() + 5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Graceful shutdown incomplete: {}", e.toString());
        } finally {
            server.shutdownNow();
            closeQuietly(dataSource);
        }
        log.info("Shutdown complete");
    }

    private boolean databaseAnswers() {
        if (dataSource == null) {
            return true;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException e) {
            log.warn("Readiness check: database unavailable ({})", e.getMessage());
            return false;
        }
    }

    private static void closeQuietly(HikariDataSource dataSource) {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
