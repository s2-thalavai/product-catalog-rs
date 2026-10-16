# API collections

## Bruno: `bruno/product-catalog-rest/`

32 requests in 6 folders with **129 automated tests**. The files are plain text and work well in Git.

```
product-catalog-rest/
├── collection.bru           shared X-Correlation-Id header
├── environments/            local · docker-compose (:8080) · k3d-port-forward (:18080)
├── 0 Discovery/             OpenAPI document · health
├── 1 CRUD/                  create (201+Location+ETag) → get → 304 → PUT (If-Match) → 412 stale → PATCH → 409 → DELETE 204 → 404
├── 2 Representations/       XML · HTML (+CSP) · text · ?format= · create from XML
├── 3 Search and paging/     Link + X-Total-Count · filter/sort/page · keyword · size cap · past the end · 400
├── 4 Errors/                validation · malformed JSON · 406 · 415 · 405 · problem as XML · correlation id
└── 5 CORS/                  preflight from allowed origin · other origin gets nothing
```

**Desktop:** *Open Collection*, select the folder, pick the `local` environment, then right-click → **Run**.
The CRUD folder chains requests: *Create product* stores `{{sku}}`, `{{productId}}` and `{{etag}}`.

**CLI / CI:**
```bash
npm install -g @usebruno/cli
cd api-collections/bruno/product-catalog-rest
bru run --env local
bru run --env local --reporter-junit ../../../target/bruno-results.xml
```

The service must run with demo data and a CORS origin for folder 5:
```bash
STORE_TYPE=memory SEED_DATA=true CORS_ALLOWED_ORIGINS=http://localhost:3000 java -jar target/product-catalog-rs.jar
```
`make run` sets these for you. Docker Compose and the k3s overlay already configure the origin.

## Other clients

- `../http-samples/catalog.http`: one-click requests for the IntelliJ IDEA HTTP Client or VS Code REST Client.
- **Postman / Insomnia / Swagger Editor:** import `http://localhost:8080/openapi.json`.
- **SoapUI** also handles REST: *New REST Project* → import the OpenAPI URL.
