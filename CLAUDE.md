# CLAUDE.md

Guidance for Claude Code (and humans) working in this repository.

## What this is

A **REST API** for a product catalog built with **JAX-RS (Jakarta RESTful Web Services) on Jersey 3**,
served by an embedded **Grizzly** HTTP server (no servlet container). CRUD, search and pagination,
with every response available as **JSON, XML, HTML or plain text**. Data lives in PostgreSQL
(or in memory locally). It ships as a Docker image deployed to Kubernetes (k3s/k3d) by Jenkins.

It is the REST twin of the `product-catalog-ws` SOAP project: `domain/`, `service/` and `repository/`
are the same code. Only the `api/` layer differs. It's a learning project for the Spring REST course.

## Commands

| Task | Command |
|---|---|
| Build jar (no tests) | `mvn -B -ntp clean package -DskipTests` |
| All tests + coverage gate | `mvn -B -ntp clean verify` (Testcontainers IT needs Docker; skipped without it) |
| Unit tests only | `mvn -B -ntp test` |
| One test | `mvn -Dtest=RenderingTest test` · `mvn -Dit.test=ProductApiIT -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false verify` |
| Run locally | `make run` (= in-memory store, demo data, CORS for localhost:3000) |
| Try it | `curl -s localhost:8080/api/v1/products?size=3 -H 'Accept: text/plain'` |
| Smoke test | `scripts/smoke-test.sh http://localhost:8080` |
| API collection | `cd api-collections/bruno/product-catalog-rest && bru run --env local` |
| Update OpenAPI baseline | `scripts/update-contract.sh` (only for intended, compatible changes) |
| Render k8s | `kubectl kustomize k8s/overlays/k3s` |

`make help` lists all shortcuts. Full setup lives in `HOW_TO_RUN.md`.

## Architecture

```
api/                 JAX-RS layer (the only HTTP-aware code)
  ProductResource    endpoints; thin: parse -> service -> map -> Response
  HealthResource     /health/live, /health/ready, / -> 303
  SearchParams       @BeanParam: query params parsed as strings -> 400 with every error
  SortParser         sort=field[,asc|desc] whitelist
  PageLinks, Etags   Link header relations, entity tags
  ApiMapper          DTO <-> domain
  JsonMapperFactory  the one Jackson configuration
  OpenApiSetup       /openapi.json from annotations (swagger-jaxrs2)
  dto/               JAXB + Jackson DTOs (FIELD access: JSON and XML share names)
  problem/           RFC 9457 ProblemDetail, ErrorCode, Problems (mapping + media type choice)
  mapper/            ExceptionMappers -> problems (domain, framework, JSON parse, unhandled)
  render/            HTML + text: pure renderers + MessageBodyWriters
  filter/            correlation id, ?format=, CORS, security headers
service/             business rules + validation (ProductService, ProductValidator, PagingPolicy)
repository/          ProductRepository: InMemory | Jdbc (PostgreSQL); shared contract test
domain/              records + domain exceptions, no framework imports
infra/               Hikari, Flyway, JUL->SLF4J
config/AppConfig     typed env-var configuration, validated at startup
Application          composition root: ResourceConfig, Grizzly, graceful shutdown
```

Request flow: `CorrelationIdFilter` → `FormatParameterFilter` → Jersey routing + content negotiation
→ `ProductResource` → `ProductService` → repository → DTO → Jackson / JAXB / HTML / text writer →
`CorsFilter` and `SecurityHeadersFilter` → response.

## Rules to follow

**Layering**
- Only `api/` may import `jakarta.ws.rs`, Jackson, JAXB or swagger. `domain/` and `service/` stay framework-free
  (they are shared with the SOAP project).
- Resources stay thin. Put logic in the service, or in a pure helper in `api/` with a unit test
  (as with `SortParser`, `PageLinks` and `Problems`).
- Providers are registered **explicitly** in `Application.resourceConfig`. Classpath discovery is off
  (`METAINF_SERVICES_LOOKUP_DISABLE`). A new filter, mapper or writer does nothing until you register it.

**HTTP semantics**
- Paths: `/api/v1/<plural-noun>`; ids are numeric (`{id: [0-9]+}`).
- POST → 201 + `Location` + `ETag`. PUT replaces (404 if missing, no upsert). PATCH = merge patch
  (null/absent = unchanged, `""` clears an optional string). DELETE → 204.
- GET one → `ETag` + `Cache-Control: no-cache`; `If-None-Match` → 304. PUT/PATCH/DELETE honour `If-Match` → 412.
- Collections are always paginated: `page` (0-based), `size` (default 20, capped at `PAGE_SIZE_MAX`),
  `X-Total-Count`, and a `Link` header with first/prev/next/last that keeps the current filters.
- Query parameters arrive as `String` and are parsed in `SearchParams` (an unparsable `@QueryParam Integer`
  would give 404 per the JAX-RS spec).

**Representations**
- Each resource method `@Produces` JSON (qs=1), XML (0.9), text/xml, HTML (0.8) and text (0.7). The qs values
  make `*/*` → JSON while browsers get HTML. `?format=json|xml|html|text` overrides Accept.
- DTOs: classes with a no-arg constructor (JAXB can't do records), `@XmlRootElement`, `@XmlAccessorType(FIELD)`.
  Jackson is configured for field visibility, so a field name is the name in JSON and XML. Don't add getters
  expecting them to change JSON.
- Money is `BigDecimal` (plain notation); dates are `Instant` (ISO-8601 UTC); nulls are omitted; unknown JSON
  properties are ignored (tolerant reader).
- HTML is escaped via `HtmlRenderer.escape` (always) and served with a script-free CSP. No JavaScript.

**Errors**
- Every 4xx/5xx is an RFC 9457 problem (`application/problem+json` | `+xml` | HTML | text, chosen from Accept)
  with `errorCode` (stable, from `ErrorCode`), `traceId` and optional `errors[]`.
- Business failures: throw domain exceptions (`ProductNotFoundException` 404, `DuplicateSkuException` 409,
  `InvalidRequestException` 400); `CatalogExceptionMapper` maps them. Never build error Responses in resources.
- Never leak stack traces, SQL, class names or Jackson messages. `JsonProcessingExceptionMapper` names the
  field only.

**Validation & persistence**: same rules as the service layer. Every string is bounded (limits = column sizes
in `V1__create_product_table.sql`), all violations are reported at once, SQL uses bind parameters only, ORDER BY comes
only from the `SortField` whitelist, and Flyway migrations are immutable (add `V2__…`). Keep `SeedData.java` in sync
with `db/seed`.

**API contract**
- `OpenApiContractIT` diffs `/openapi.json` (keys sorted) with `src/test/resources/contract/openapi.json`.
  Additive changes only (new optional fields, params, endpoints). Breaking change → `/api/v2`.
- Annotate new endpoints with `@Operation` + `@ApiResponse` (including problem responses).

**Security**
- CORS: explicit origins only (`CORS_ALLOWED_ORIGINS`; `*` is rejected at startup), no credentials.
- Correlation ids are validated (`[A-Za-z0-9._-]{1,64}`) before reaching logs. Never log bodies or secrets;
  `AppConfig.toString()` masks the DB password.

**Kubernetes**: `k8s/base/app` is environment-neutral. Namespace `catalog-rs`, resources prefixed
`product-catalog-rs`, so the SOAP project can share the same k3d cluster (`catalog`). Config via
`configMapGenerator` (hash → rollout); prod secrets are never committed. Pods run as non-root with a
read-only root filesystem under the `restricted` Pod Security Standard.

## Testing expectations

- Pure logic → plain unit tests (`RequestParsingTest`, `HttpSemanticsTest`, `ProblemsTest`, `RenderingTest`,
  `FiltersTest`, `ProductServiceTest`, `AppConfigTest`).
- Storage → `ProductRepositoryContract` (in-memory + PostgreSQL via Testcontainers).
- HTTP behaviour (status codes, headers, negotiation, problems, ETags, CORS) → `ProductApiIT` over real HTTP.
- Black-box regression → Bruno collection (`api-collections/`) and `scripts/smoke-test.sh` (also run by Jenkins).
- JaCoCo line coverage gate: `coverage.minimum` in `pom.xml` (0.65).

## Gotchas

- `javax.ws.rs` is the old namespace; this project uses `jakarta.ws.rs` (Jersey 3.x).
- The fat jar needs shade's `ServicesResourceTransformer` (Jersey, HK2, JAXB, Flyway, SLF4J use `META-INF/services`).
  `METAINF_SERVICES_LOOKUP_DISABLE` only affects JAX-RS provider discovery, not those.
- Jersey's built-in OPTIONS handling answers CORS preflights; `CorsFilter` decorates that response.
- Header names come back in any case: compare case-insensitively.
- `/openapi.json` comes from `swagger-jaxrs2-jakarta`; HealthResource is `@Hidden`.
- In-memory store = per-pod data. Only use `STORE_TYPE=memory` locally.

## Known gaps / roadmap

- No authentication yet. Planned: OAuth2/JWT bearer tokens (a `ContainerRequestFilter` validating JWTs)
  with per-endpoint authorization.
- If-Match is check-then-write (not atomic). For strict optimistic locking add a `version` column and
  `UPDATE … WHERE id = ? AND version = ?`.
- Keyword search uses `LIKE '%…%'`. Add `pg_trgm` or full-text search for large catalogs.
- No metrics endpoint yet (Micrometer + Prometheus) and no rate limiting.
- Next in the course: the same API with Spring Boot, reusing `service/`, `repository/` and `domain/`.
