# How to run

| # | What | You need |
|---|---|---|
| 1 | [Run locally, in-memory](#1-run-locally-in-memory) | JDK 21, Maven |
| 2 | [Run with PostgreSQL (Docker Compose)](#2-run-with-postgresql-docker-compose) | Docker |
| 3 | [Call the API](#3-call-the-api) | curl / browser / Bruno / Postman |
| 4 | [Run the tests](#4-run-the-tests) | JDK 21, Maven, Docker (for one IT) |
| 5 | [Kubernetes on k3d (recommended local cluster)](#5-kubernetes-on-k3d-k3s-in-docker) | Docker, k3d, kubectl |
| 6 | [Kubernetes on native k3s (Linux / WSL2 / VM)](#6-kubernetes-on-native-k3s) | Linux host, Docker |
| 7 | [Jenkins CI/CD](#7-jenkins-cicd-local-lab) | Docker, path 5 done |
| 8 | [Production deployment](#8-production-deployment) | cluster + registry + managed PostgreSQL |

Configuration reference and troubleshooting are at the end.

---

## 0. Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Docker | 24+ (Compose v2, BuildKit) | `docker version` |
| kubectl | 1.29+ | `kubectl version --client` |
| k3d | 5.7+ | `k3d version` (`brew install k3d` · `choco install k3d` · `curl -s https://raw.githubusercontent.com/k3d-io/k3d/main/install.sh \| bash`) |
| Bruno CLI (optional) | latest | `npm i -g @usebruno/cli` |

**Windows:** run the `.sh` scripts from **Git Bash** or **WSL2**. If one fails with `bad interpreter: /bin/bash^M`,
run `git config core.autocrlf input` and re-clone.

---

## 1. Run locally (in-memory)

```bash
mvn -B -ntp clean package -DskipTests
STORE_TYPE=memory SEED_DATA=true CORS_ALLOWED_ORIGINS=http://localhost:3000 java -jar target/product-catalog-rs.jar
# or: make run
```
PowerShell:
```powershell
$env:STORE_TYPE="memory"; $env:SEED_DATA="true"; $env:CORS_ALLOWED_ORIGINS="http://localhost:3000"
java -jar target\product-catalog-rs.jar
```

| URL | What |
|---|---|
| http://localhost:8080/api/v1/products | The API. A browser gets the **HTML** view with search and paging |
| http://localhost:8080/api/v1/products?format=json | Same data as JSON (`xml`, `text` work too) |
| http://localhost:8080/openapi.json | OpenAPI 3 document (also `/openapi.yaml`) |
| http://localhost:8080/health/live · /health/ready | Probes |

`SEED_DATA=true` loads 25 demo products in 5 categories. Stop with Ctrl+C (graceful shutdown).

---

## 2. Run with PostgreSQL (Docker Compose)

```bash
docker compose up -d --build        # PostgreSQL 16 + API, demo data seeded
docker compose logs -f app          # Flyway migration, then "Listening on port 8080"
docker compose down                 # stop (data kept)
docker compose down -v              # stop and delete the database volume
```
Port clash (for example the SOAP project is running)? `APP_PORT=9080 PG_PORT=15432 docker compose up -d`.

---

## 3. Call the API

### Endpoints

| Method | Path | Body | Success | Errors (`errorCode`) |
|---|---|---|---|---|
| GET | `/api/v1/products` | – | 200 page + `Link` + `X-Total-Count` | 400 `VALIDATION_FAILED` |
| POST | `/api/v1/products` | product (JSON/XML) | **201** + `Location` + `ETag` | 400, 409 `DUPLICATE_SKU`, 415 |
| GET | `/api/v1/products/{id}` | – | 200 + `ETag` · **304** with `If-None-Match` | 404 `PRODUCT_NOT_FOUND` |
| PUT | `/api/v1/products/{id}` | full product | 200 + new `ETag` | 400, 404, 409, **412** with stale `If-Match` |
| PATCH | `/api/v1/products/{id}` | changed fields (`application/merge-patch+json`, JSON, XML) | 200 | 400, 404, 409, 412 |
| DELETE | `/api/v1/products/{id}` | – | **204** | 404, 412 |

**Search parameters** (all optional): `q` (keyword in name/SKU/description), `category`, `minPrice`, `maxPrice`,
`inStock=true`, `page` (0-based), `size` (default 20, max 100), `sort=field[,asc|desc]` with
field = `id | name | price | stock | createdAt`.

**Representations:** send `Accept: application/json | application/xml | text/html | text/plain`, or add
`?format=json|xml|html|text`. With no preference you get JSON. Request bodies can be JSON or XML.

### curl
```bash
API=http://localhost:8080/api/v1/products

curl -s "$API?size=5"                                             # JSON
curl -s "$API?size=5" -H 'Accept: text/plain'                     # aligned text table
curl -s "$API/1" -H 'Accept: application/xml'                     # XML
curl -si "$API?category=electronics&minPrice=1000&maxPrice=9000&inStock=true&sort=price,desc&size=3"   # see Link header

curl -si -X POST "$API" -H 'Content-Type: application/json' \
  -d '{"sku":"FIT-KBEL-01","name":"Kettlebell 8 kg","category":"Fitness","price":1899.00,"stock":25}'

curl -si "$API/1"                                                  # note the ETag
curl -si "$API/1" -H 'If-None-Match: "<etag>"'                     # 304 Not Modified
curl -si -X PATCH "$API/1" -H 'Content-Type: application/merge-patch+json' -H 'If-Match: "<etag>"' -d '{"stock":80}'
curl -si -X DELETE "$API/2"                                        # 204

curl -s "$API/99999" | jq                                          # RFC 9457 problem
```
PowerShell: `Invoke-RestMethod "http://localhost:8080/api/v1/products?size=5"` (it parses JSON for you).

### Browser
Open http://localhost:8080/api/v1/products to search, sort and page through the catalog. Each page links
to its JSON, XML and text versions.

### Collections and clients
- **Bruno:** open `api-collections/bruno/product-catalog-rest` and run it (32 requests, 129 tests). CLI:
  `cd api-collections/bruno/product-catalog-rest && bru run --env local`
- **IntelliJ / VS Code REST Client:** `http-samples/catalog.http`
- **Postman / Insomnia / Swagger Editor:** import `http://localhost:8080/openapi.json`

### Smoke test (one command)
```bash
./scripts/smoke-test.sh http://localhost:8080              # creates and cleans up its own product
./scripts/smoke-test.sh http://localhost:8080 --read-only  # no writes
```

---

## 4. Run the tests

```bash
mvn -B -ntp clean verify      # or: make test
```

| Suite | Covers | Docker |
|---|---|---|
| Unit (`*Test`) | config, service + PATCH rules, query parsing, sort whitelist, Link relations, ETags, CORS, problem mapping, HTML/text rendering, in-memory repository | no |
| `ProductApiIT` | real HTTP: CRUD in JSON and XML, 4 representations, 304/412, Link/X-Total-Count, 400/404/405/406/409/415, CORS, correlation id, OpenAPI | no |
| `JdbcProductRepositoryIT` | repository contract on PostgreSQL 16 (Testcontainers) | **yes** (skipped otherwise) |
| `OpenApiContractIT` | `/openapi.json` vs committed baseline | no |

Reports: `target/surefire-reports`, `target/failsafe-reports`; coverage `target/site/jacoco/index.html` (gate 65 %).

**OpenAPI baseline (do once):** the first build skips `OpenApiContractIT`. Create the baseline and commit it:
```bash
./scripts/update-contract.sh && git add src/test/resources/contract
```

---

## 5. Kubernetes on k3d (k3s in Docker)

Works on Windows, macOS and Linux with no registry. It can share the `catalog` cluster with the SOAP project:
this API lives in namespace `catalog-rs` with host `catalog-rs.localhost`.

```bash
./infra/k3d/create-cluster.sh     # cluster "catalog", ingress -> localhost:8081 (reused if it exists)
./infra/k3d/deploy-local.sh       # docker build -> k3d image import -> kubectl apply -k k8s/overlays/k3s
```
This deploys the API (2 replicas, HPA 2-5, PDB), a PostgreSQL StatefulSet (1 Gi) with a NetworkPolicy, and a
Traefik ingress for `/api` and `/openapi*` on host `catalog-rs.localhost`. CORS allows `http://localhost:3000`
and `:5173`.

```bash
kubectl -n catalog-rs get pods,svc,ingress,hpa
curl -s -H 'Host: catalog-rs.localhost' 'http://localhost:8081/api/v1/products?size=3' -H 'Accept: text/plain'
./scripts/smoke-test.sh http://localhost:8081 --host catalog-rs.localhost

kubectl -n catalog-rs port-forward svc/product-catalog-rs 18080:80     # then use Bruno env "k3d-port-forward"
kubectl -n catalog-rs logs -l app.kubernetes.io/name=product-catalog-rs -f --prefix
./infra/k3d/delete-cluster.sh                                           # removes the whole cluster
```

---

## 6. Kubernetes on native k3s

```bash
./infra/k3s/install-k3s.sh                 # Linux / VM / WSL2 with systemd; writes ~/.kube/config-k3s
export KUBECONFIG=~/.kube/config-k3s
./infra/k3s/deploy-local.sh                # build, import into k3s containerd (sudo), deploy
./scripts/smoke-test.sh http://localhost --host catalog-rs.localhost
```

---

## 7. Jenkins CI/CD (local lab)

Pipeline (`Jenkinsfile`): **checkout → `mvn verify` (unit, IT, Testcontainers, OpenAPI contract, coverage gate)
→ Docker image → Trivy scan → publish (k3d import or registry push) → manual approval (prod) → deploy with
kustomize → smoke test → automatic `rollout undo` on failure.**

1. Push this project to a Git repository Jenkins can read.
2. `./infra/k3d/create-cluster.sh` (writes `infra/jenkins/secrets/kubeconfig-k3s`).
3. ```bash
   cp infra/jenkins/.env.example infra/jenkins/.env     # admin password + GIT_REPO_URL
   ./infra/jenkins/start-jenkins.sh                      # http://localhost:8091
   ```
4. Job **product-catalog-rs → Build with Parameters**: `DEPLOY_ENV=k3s`, `IMAGE_DELIVERY=k3d-import`.

This Jenkins uses port **8091** and container `catalog-rs-jenkins`, so it can run next to the SOAP project's (8090).

| Credential id | Type | Created by | Used for |
|---|---|---|---|
| `kubeconfig-k3s` | Secret file | JCasC (from `infra/jenkins/secrets/`) | deploy to k3d |
| `kubeconfig-prod` | Secret file | you | deploy to prod |
| `registry-creds` | Username/password | you | `IMAGE_DELIVERY=registry-push` |

Jenkins runs as root with the host Docker socket, which is fine **for a local lab only**. In a shared Jenkins use agents.

---

## 8. Production deployment

`k8s/overlays/prod`: API only (managed PostgreSQL), 3-10 replicas spread across nodes, NGINX ingress with TLS.

1. Replace every `CHANGE_ME` (DB host, registry, hostname, CORS origin).
2. The cluster needs ingress-nginx and metrics-server, and pull access to your registry (AKS: `az aks update --attach-acr <acr>`).
3. Create the secrets out of band:
   ```bash
   kubectl create namespace catalog-rs
   kubectl -n catalog-rs create secret generic product-catalog-rs-db --from-literal=username='<user>' --from-literal=password='<password>'
   kubectl -n catalog-rs create secret tls product-catalog-rs-tls --cert=tls.crt --key=tls.key
   ```
4. Jenkins: `DEPLOY_ENV=prod`, `IMAGE_DELIVERY=registry-push`, `REGISTRY=<registry>`. The pipeline asks for
   approval, and the smoke test runs read-only.

---

## Configuration reference

| Variable | Default | Meaning |
|---|---|---|
| `APP_PORT` | `8080` | HTTP port (0 = random, used by tests) |
| `STORE_TYPE` | `memory` | `memory` or `postgres` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | – | Required for `postgres` (password from a Secret) |
| `DB_POOL_SIZE` | `10` | Hikari max pool size |
| `SEED_DATA` | `false` | Load the 25 demo products |
| `HTTP_THREADS` | `32` | Grizzly worker threads |
| `PAGE_SIZE_DEFAULT` / `PAGE_SIZE_MAX` | `20` / `100` | Paging default and cap |
| `CORS_ALLOWED_ORIGINS` | empty (CORS off) | Comma-separated explicit origins; `*` is rejected |
| `SHUTDOWN_GRACE_SECONDS` | `10` (k8s: `20`) | Time for in-flight requests on SIGTERM |
| `LOG_LEVEL` | `INFO` | Level for `com.infy.catalog` |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` | JVM flags (image) |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Address already in use` | `APP_PORT=9080 java -jar …` |
| Browser shows HTML, you wanted JSON | Add `?format=json`, or send `Accept: application/json` |
| `406 Not Acceptable` | Accept header has no supported type; use JSON, XML, HTML or text |
| `415 Unsupported Media Type` | Set `Content-Type: application/json` (or XML; PATCH also takes `application/merge-patch+json`) |
| `412 Precondition Failed` | Your `If-Match` ETag is stale: GET again, retry with the new ETag |
| CORS error in the browser | Add your page's origin to `CORS_ALLOWED_ORIGINS` (exact scheme+host+port) |
| `bad interpreter: /bin/bash^M` | CRLF line endings, see Prerequisites |
| `JdbcProductRepositoryIT` skipped | Docker unavailable to Maven; start Docker or use `-Dcoverage.minimum=0.0` |
| `OpenApiContractIT` skipped / failing | No baseline yet / API changed: see section 4 |
| Pod `CreateContainerConfigError` | Secret `product-catalog-rs-db` missing in `catalog-rs` |
| Pod `ImagePullBackOff` on k3d | `k3d image import product-catalog-rs:local -c catalog` |
| Readiness 503 | DB unreachable: `kubectl -n catalog-rs logs deploy/product-catalog-rs` |
| Ingress 404 | Send `-H 'Host: catalog-rs.localhost'` |
| Jenkins can't reach the cluster | Rerun `./infra/jenkins/start-jenkins.sh` (joins the k3d network) |
