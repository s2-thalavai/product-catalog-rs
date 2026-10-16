# Product Catalog – REST API (JAX-RS)

![Java 21](https://img.shields.io/badge/Java-21-orange)
![JAX-RS](https://img.shields.io/badge/JAX--RS-Jersey%203.1-blue)
![Formats](https://img.shields.io/badge/formats-JSON%20%7C%20XML%20%7C%20HTML%20%7C%20text-6f42c1)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)
![Kubernetes](https://img.shields.io/badge/Kubernetes-k3s%20%7C%20k3d-326CE5)
![Jenkins](https://img.shields.io/badge/CI-Jenkins-D24939)

A production-style **REST API** for a product catalog: CRUD, keyword search, filtering, sorting and pagination,
built with **JAX-RS (Jersey 3)** on an embedded Grizzly server.

- **One URL, four formats:** JSON, XML, HTML (browsable) or plain text, chosen by the `Accept` header or `?format=`.
- **HTTP done properly:** status codes, `Location`, ETags with 304/412, `Link` pagination headers, and RFC 9457 errors.
- **Ready to ship:** PostgreSQL + Flyway, Docker, Kubernetes (k3s/k3d), and a Jenkins pipeline with deploy, smoke test and rollback.

It is the REST twin of `product-catalog-ws` (SOAP/JAX-WS). Both share the same domain, service and repository
code, so the two web-service styles can be compared line by line.

---

## Contents

1. [Try it in 60 seconds](#try-it-in-60-seconds)
2. [Architecture](#architecture)
3. [API reference](#api-reference)
4. [Content negotiation](#content-negotiation)
5. [Errors](#errors)
6. [Testing](#testing)
7. [Deployment](#deployment)
8. [Configuration](#configuration)
9. [SOAP vs REST in these two projects](#soap-vs-rest-in-these-two-projects)
10. [Design decisions](#design-decisions)
11. [Project layout](#project-layout)
12. [Tech stack and roadmap](#tech-stack)

---

## Try it in 60 seconds

**Prerequisites:** JDK 21 and Maven 3.9+. Docker is only needed for PostgreSQL and Kubernetes.

```bash
mvn -B -ntp clean package -DskipTests
STORE_TYPE=memory SEED_DATA=true CORS_ALLOWED_ORIGINS=http://localhost:3000 \
  java -jar target/product-catalog-rs.jar          # or simply: make run
```

| Try | What you'll see |
|---|---|
| Open http://localhost:8080/api/v1/products | HTML catalog with search form, sorting and paging |
| `curl -s 'localhost:8080/api/v1/products?size=5' -H 'Accept: text/plain'` | Aligned text table |
| `curl -s localhost:8080/api/v1/products/1` | JSON |
| `curl -s localhost:8080/api/v1/products/1 -H 'Accept: application/xml'` | XML |
| `curl -s localhost:8080/openapi.json` | OpenAPI 3 document |
| `./scripts/smoke-test.sh http://localhost:8080` | 17 end-to-end checks |

<details>
<summary>Windows PowerShell</summary>

```powershell
$env:STORE_TYPE="memory"; $env:SEED_DATA="true"; $env:CORS_ALLOWED_ORIGINS="http://localhost:3000"
java -jar target\product-catalog-rs.jar
Invoke-RestMethod "http://localhost:8080/api/v1/products?size=5"
```
Run the `.sh` scripts from Git Bash or WSL2.
</details>

---

## Architecture

```
                         ┌──────────────── Kubernetes namespace "catalog-rs" ─────────────────┐
  Browser ── HTML ──┐    │                                                                    │
                    ├───►│  Ingress ──► Service ──► Deployment: 2–5 pods (HPA, PDB)           │
  curl / apps ──────┘    │  /api, /openapi            │                                       │
  JSON · XML · text      │                            │ JDBC (HikariCP)                       │
                         │                            ▼                                       │
                         │               PostgreSQL 16 StatefulSet  (k3s / dev)               │
                         │               Managed PostgreSQL         (prod)                    │
                         └────────────────────────────────────────────────────────────────────┘
```

**Inside a pod: one request**

```
 HTTP request
     │
     ▼
 Filters ─ CorrelationId (traceId) ─ ?format= → Accept ─────────────────────────┐
     │                                                                          │
     ▼                                                                          │
 Jersey routing + content negotiation                                           │
     │                                                                          │
     ▼                                                                          │
 ProductResource ──► ProductService ──► ProductRepository (JDBC | in-memory)    │
     │                     │                                                    │
     │               domain exception                                           │
     │                     ▼                                                    │
     │               ExceptionMappers ──► RFC 9457 ProblemDetail                │
     ▼                     │                                                    │
 MessageBodyWriter ◄───────┘   Jackson (JSON) · JAXB (XML) · HTML · text        │
     │                                                                          │
     ▼                                                                          │
 Filters ─ CORS ─ security headers ─ access log ◄───────────────────────────────┘
     │
     ▼
 HTTP response
```

| Layer | Package | Knows about HTTP? | Shared with SOAP project |
|---|---|---|---|
| API | `api` | yes | no |
| Business rules | `service` | no | **yes** |
| Storage | `repository` | no | **yes** |
| Model | `domain` | no | **yes** |

---

## API reference

Base path `/api/v1/products` · full schema at `/openapi.json`.

| Method | Path | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/api/v1/products` | – | **200** + `Link`, `X-Total-Count` | 400 |
| POST | `/api/v1/products` | product (JSON or XML) | **201** + `Location`, `ETag` | 400, 409, 415 |
| GET | `/api/v1/products/{id}` | – | **200** + `ETag` · **304** if `If-None-Match` matches | 404 |
| PUT | `/api/v1/products/{id}` | full product | **200** | 400, 404, 409, 412 |
| PATCH | `/api/v1/products/{id}` | changed fields only (`application/merge-patch+json`) | **200** | 400, 404, 409, 412 |
| DELETE | `/api/v1/products/{id}` | – | **204** | 404, 412 |

### Search and pagination parameters

| Parameter | Example | Meaning |
|---|---|---|
| `q` | `q=mouse` | Keyword in name, SKU or description (case-insensitive) |
| `category` | `category=electronics` | Exact category (case-insensitive) |
| `minPrice` / `maxPrice` | `minPrice=1000&maxPrice=9000` | Inclusive price range |
| `inStock` | `inStock=true` | Only products with stock > 0 |
| `sort` | `sort=price,desc` | `id`, `name`, `price`, `stock` or `createdAt`, optional `,asc`/`,desc` |
| `page` / `size` | `page=1&size=10` | 0-based page; size defaults to 20, **capped at 100** |

```http
GET /api/v1/products?category=electronics&inStock=true&sort=price,desc&size=3

HTTP/1.1 200 OK
Content-Type: application/json
X-Total-Count: 6
Link: <…&size=3&page=0>; rel="first", <…&size=3&page=1>; rel="next", <…&size=3&page=1>; rel="last"

{"items":[{"id":4,"sku":"ELEC-HEAD-01","name":"Noise Cancelling Headphones","price":8999.00,…}, …],
 "page":0,"size":3,"totalElements":6,"totalPages":2,"hasNext":true,"hasPrevious":false}
```

### Create, then update safely with ETags

```bash
curl -si -X POST localhost:8080/api/v1/products -H 'Content-Type: application/json' \
  -d '{"sku":"FIT-KBEL-01","name":"Kettlebell 8 kg","category":"Fitness","price":1899.00,"stock":25}'
# 201 Created · Location: http://localhost:8080/api/v1/products/26 · ETag: "1a-…"

curl -si -X PATCH localhost:8080/api/v1/products/26 \
  -H 'Content-Type: application/merge-patch+json' -H 'If-Match: "1a-…"' -d '{"stock":20}'
# 200 OK with a new ETag, or 412 Precondition Failed if someone changed it in between
```

More ready-to-run requests: `http-samples/catalog.http` (IntelliJ / VS Code) and the Bruno collection in `api-collections/`.

---

## Content negotiation

The same resource, four representations:

| Ask with | You get | Good for |
|---|---|---|
| `Accept: application/json` (or nothing, or `*/*`) | JSON | apps, scripts |
| `Accept: application/xml` | XML (`<product>`, `<productPage>`) | enterprise integrations |
| `Accept: text/html` (any browser) | HTML page with search, sort and paging, no JavaScript | humans |
| `Accept: text/plain` | aligned text table | terminals, logs |
| `?format=json\|xml\|html\|text` | overrides `Accept` | quick links, browser testing |

Request bodies can be **JSON or XML**. Anything else gives **415**, and an unsupported `Accept` gives **406**.

```text
$ curl -s 'localhost:8080/api/v1/products?category=books' -H 'Accept: text/plain'
ID  SKU           NAME                           CATEGORY    PRICE  STOCK
--  ------------  -----------------------------  --------  -------  -----
18  BOOK-JAVA-01  Java Design Patterns Handbook  Books     1499.00     30
19  BOOK-K8S-01   Kubernetes in Practice         Books     2499.00     22
20  BOOK-SQL-01   SQL for Developers             Books     1199.00     64

3 product(s), page 1 of 1, page size 20
```

---

## Errors

Every 4xx/5xx is an **RFC 9457 problem**, in the same format the client asked for
(`application/problem+json`, `application/problem+xml`, HTML or text).

```json
{
  "type": "https://catalog.infy.com/problems/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request has 2 invalid field(s); see errors.",
  "instance": "/api/v1/products",
  "errorCode": "VALIDATION_FAILED",
  "traceId": "my-request-42",
  "errors": ["name is required", "price must be >= 0"]
}
```

| HTTP | `errorCode` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Invalid fields or query parameters (all reported at once) |
| 400 | `MALFORMED_REQUEST` | Body is not valid JSON/XML or has a wrong value type |
| 404 | `PRODUCT_NOT_FOUND` / `NOT_FOUND` | Unknown id / unknown path |
| 405 / 406 / 415 | `METHOD_NOT_ALLOWED` / `NOT_ACCEPTABLE` / `UNSUPPORTED_MEDIA_TYPE` | Wrong method, `Accept` or `Content-Type` |
| 409 | `DUPLICATE_SKU` | SKU already used by another product |
| 412 | `PRECONDITION_FAILED` | `If-Match` ETag is stale |
| 500 | `INTERNAL_ERROR` | Unexpected; details only in the server log, under the same `traceId` |

Send `X-Correlation-Id: <id>` and it comes back as the response header, as the problem's `traceId`, and in every log line.
Errors never contain stack traces, SQL or Java class names.

---

## Testing

```bash
mvn -B -ntp clean verify
```

| Layer | Where | What it proves | Needs |
|---|---|---|---|
| Unit | `src/test/java/**/*Test.java` | validation, PATCH rules, query parsing, sort whitelist, Link relations, ETags, CORS, error mapping, HTML/text rendering | – |
| HTTP integration | `ProductApiIT` | real server: CRUD in JSON and XML, all formats, 304/412, all error codes, CORS, correlation id | – |
| Database | `JdbcProductRepositoryIT` | same repository contract on PostgreSQL 16 (Testcontainers) | Docker |
| API contract | `OpenApiContractIT` | `/openapi.json` unchanged vs committed baseline | – |
| Black-box | `api-collections/bruno` (32 requests, 129 tests), `scripts/smoke-test.sh` | any running instance; Jenkins runs the smoke test after each deploy | running API |

The first build skips the contract test until a baseline exists: run `./scripts/update-contract.sh` once and commit it.
Coverage report: `target/site/jacoco/index.html` (gate: 65 % lines).

---

## Deployment

| Target | Command | Result |
|---|---|---|
| Docker Compose | `docker compose up -d --build` | API + PostgreSQL on `localhost:8080` |
| k3d (k3s in Docker, any OS) | `make k3d-up && make k3d-deploy` | `http://localhost:8081` with `Host: catalog-rs.localhost` |
| Native k3s (Linux/WSL2) | `./infra/k3s/install-k3s.sh && ./infra/k3s/deploy-local.sh` | Traefik on port 80 |
| Jenkins (local lab) | `make jenkins-up` | http://localhost:8091, job `product-catalog-rs` |
| Production | Jenkins with `DEPLOY_ENV=prod` | manual approval, read-only smoke test |

**Pipeline**

```
 Checkout ─► mvn verify ─► Docker build ─► Trivy scan ─► Publish ─► [prod: approval] ─► Deploy ─► Smoke test
             tests ·                                     k3d import                     kustomize       │
             contract ·                                  or registry                                    │ fail
             coverage                                                                                   ▼
                                                                                              kubectl rollout undo
```

It can share the k3d cluster with the SOAP project: different namespace (`catalog-rs`), host (`catalog-rs.localhost`)
and Jenkins port (8091). Step-by-step instructions: **[HOW_TO_RUN.md](HOW_TO_RUN.md)**.

---

## Configuration

Environment variables (a ConfigMap in Kubernetes). The most used ones:

| Variable | Default | Purpose |
|---|---|---|
| `STORE_TYPE` | `memory` | `memory` or `postgres` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | – | required for `postgres` |
| `SEED_DATA` | `false` | load 25 demo products |
| `PAGE_SIZE_DEFAULT` / `PAGE_SIZE_MAX` | `20` / `100` | paging default and cap |
| `CORS_ALLOWED_ORIGINS` | empty (off) | explicit origins only; `*` is rejected |
| `APP_PORT` | `8080` | HTTP port |

Full list: [HOW_TO_RUN.md → Configuration reference](HOW_TO_RUN.md#configuration-reference).

---

## SOAP vs REST in these two projects

Same catalog, same business rules, two styles:

| | `product-catalog-ws` (SOAP) | `product-catalog-rs` (REST) |
|---|---|---|
| Java API | JAX-WS (Metro) | JAX-RS (Jersey) |
| Addressing | one endpoint, operation in the body (`<cat:getProduct>`) | one URI per resource (`/products/1`) |
| Actions | operation names, always `POST` | HTTP methods: GET, POST, PUT, PATCH, DELETE |
| Formats | XML in a SOAP envelope | JSON, XML, HTML, text |
| Contract | WSDL + XSD | OpenAPI 3 |
| Errors | SOAP Fault, always HTTP 500, `code` in the detail | RFC 9457 problem with the right HTTP status |
| Caching | none (POST isn't cacheable) | ETag + 304, `Cache-Control` |
| Concurrency control | – | `If-Match` → 412 |
| Paging metadata | in the body only | body + `Link` + `X-Total-Count` headers |
| Contract test | `WsdlContractIT` | `OpenApiContractIT` |
| Try it with | SoapUI, raw envelopes | browser, curl, Bruno |

---

## Design decisions

- **Jersey on Grizzly, no servlet container:** a single runnable jar, fast startup, and a small image. It's also clear which parts are JAX-RS (the standard) and which are Jersey (the implementation).
- **Providers registered explicitly** in `Application.resourceConfig` (classpath discovery off): nothing gets activated by accident.
- **DTOs map fields for both Jackson and JAXB:** a field name is the same in JSON and XML, so the formats can't drift apart.
- **Query parameters parsed by hand** (`SearchParams`): JAX-RS answers *404* for an unparsable `@QueryParam Integer`; we answer *400* listing every bad parameter.
- **Sorting via a whitelist** (`SortParser` → `SortField`): user input never reaches SQL.
- **HTML rendered server-side without JavaScript**, every value escaped, with a strict Content-Security-Policy.
- **ETag from id + updatedAt:** cheap conditional GETs and optimistic locking without extra columns. See the roadmap for atomic locking.

---

## Project layout

```
src/main/java/com/infy/catalog/
├── api/                JAX-RS layer
│   ├── ProductResource, HealthResource, SearchParams, SortParser, PageLinks, Etags
│   ├── dto/            ProductRequest, ProductPatchRequest, ProductResponse, ProductPage
│   ├── problem/        ProblemDetail, ErrorCode, Problems
│   ├── mapper/         ExceptionMappers → problems
│   ├── render/         HTML + text renderers and MessageBodyWriters
│   └── filter/         correlation id, ?format=, CORS, security headers
├── service/            business rules, validation, paging policy     (shared with SOAP)
├── repository/         in-memory + JDBC (PostgreSQL)                 (shared)
├── domain/             records and domain exceptions                 (shared)
├── infra/              Hikari, Flyway, logging bridge
├── config/             typed env-var configuration
└── Application.java    ResourceConfig, Grizzly, graceful shutdown
src/main/resources/db/  Flyway migrations + seed data
k8s/                    Kustomize: base/{app,postgres}, overlays/{k3s,prod}
infra/                  k3d, k3s and Jenkins (JCasC) setup
api-collections/        Bruno collection (129 tests)
http-samples/           catalog.http for IntelliJ / VS Code
scripts/                smoke test, OpenAPI baseline
Dockerfile · docker-compose.yml · Jenkinsfile · Makefile
```

Contributing or using Claude Code? Read **[CLAUDE.md](CLAUDE.md)**: layering rules, HTTP conventions and how to evolve the API safely.

---

## Tech stack

| Area | Technology |
|---|---|
| Runtime | Java 21, Grizzly HTTP server |
| REST | Jersey 3.1 (Jakarta RESTful Web Services), Jackson, JAXB, swagger-core (OpenAPI 3) |
| Data | PostgreSQL 16, HikariCP, Flyway |
| Logging | SLF4J, Logback (JUL bridged) |
| Testing | JUnit 5, Testcontainers, JaCoCo, Bruno |
| Delivery | Docker (multi-stage, Temurin JRE Alpine), Kustomize, k3s/k3d, Traefik, Jenkins (JCasC), Trivy |

## Roadmap

- [ ] OAuth2 / JWT authentication with per-endpoint authorization
- [ ] Atomic optimistic locking (`version` column)
- [ ] Micrometer + Prometheus metrics, rate limiting
- [ ] Spring Boot version of the same API (next course module)
