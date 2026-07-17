# nats-api-springboot-starter

Single-repo Spring Boot template: a JetStream NATS **ingest worker** and a **read API**
share one codebase, one Docker image, and one database schema. The role is chosen at
deploy time with `SPRING_PROFILES_ACTIVE=worker|api`.

## Architecture

Ingest has two independent dimensions:

```
Sources     — two NATS sources (A / B), each with its own event types and its own
              way of determining the namespace
Namespaces  — one set shared by all sources; a namespace owns the parse logic
              for turning the common payload into the normalized record payload
```

Every source follows the same flow, funneling into a single pipeline:

```
NATS message ──> consumer (per source) ──> resolve namespace ──> IngestPipeline
                                                                     │
                                              dedup via ingest_ledger│(worker-only)
                                              parse via NamespacePolicy
                                                                     ▼
                                                          ingested_record (shared)
                                                                     ▲
                                                 GET /api/namespaces/{key}/records
```

| Source | Namespace resolution |
|---|---|
| **A** | `X-Category` header **+** the common `region` body field, via `SourceANamespaceResolver` |
| **B** | declared upstream in the `X-Namespace` header |

### Shared database schema

Both roles run the same Flyway migrations (`src/main/resources/db/migration/`).

| Table | Used by | Purpose |
|---|---|---|
| `ingested_record` | worker (write), API (read) | The shared read model: every source and event type lands here, normalized by its namespace policy. `UNIQUE(source_key, dedup_key)`; indexed on `(namespace_key, occurred_at DESC)` because reads are always scoped to one namespace. |
| `ingest_ledger` | worker only | Dedup ledger keyed by `source_key + ":" + dedup_key`. The ledger entry and the record commit in the same transaction; the primary key is the backstop under concurrent redelivery. |

JPA runs with `ddl-auto: validate` — the schema is owned by Flyway, entities must match.

### Namespaces are code, enabled by config

Each namespace is a hand-written `NamespacePolicy` implementation (see
`namespace/policies/`) — namespaces may differ in parse *logic*, not just parameters.
`APP_NAMESPACES_ENABLED` is a **switch**: a namespace not listed is business-wise not
collected — the worker ACKs and drops its messages, and the API returns 404 for it.
`NamespaceRegistry` fails fast at startup if an enabled key has no policy.

## API

```
GET /api/namespaces                          -> enabled namespace keys
GET /api/namespaces/{key}/records?page=&size= -> page of records, newest first
```

There are no cross-namespace reads.

## Message handling semantics

- **SAVED / DUPLICATE / NAMESPACE_DISABLED / UNKNOWN_NAMESPACE** → `ack()`
- Malformed payload (`IllegalArgumentException`) → `term()` (poison, redelivery cannot fix it)
- Any other failure → `nak()` (redelivered)

Malformed messages also increment the `ingest.poison` counter.

## Scaling & monitoring

Workers use durable **pull** consumers: every replica fetches from the same
durable, so per-route deployments (`APP_SOURCES_ENABLED=source-a`) scale
horizontally on consumer lag (e.g. KEDA's nats-jetstream scaler).
Upgrading an existing environment: the old push consumers must be deleted
(`nats consumer rm <stream> <durable>`) before starting this version.

`/actuator/prometheus` exposes Micrometer metrics tagged `source` /
`namespace` / `result`: `ingest_messages_total`, `ingest_handle_seconds`,
`ingest_poison_total`.

## Configuration

| Env var | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | `worker` or `api` |
| `APP_NAMESPACES_ENABLED` | `alpha,beta` | Enabled namespace keys (switch semantics) |
| `APP_SOURCES_ENABLED` | `source-a,source-b` | Sources this worker subscribes (switch semantics, per-route deployments) |
| `DB_URL` | `jdbc:mariadb://localhost:3306/ingest` | JDBC URL (MariaDB) |
| `DB_USERNAME` / `DB_PASSWORD` | `ingest` / `ingest` | DB credentials |
| `NATS_URL` | `nats://localhost:4222` | NATS server (worker only) |
| `SERVER_PORT` | `8080` | HTTP port (api only) |

## Running locally

```sh
docker compose up -d          # MariaDB 11 + NATS 2.10 (JetStream)

SPRING_PROFILES_ACTIVE=worker ./mvnw spring-boot:run
SPRING_PROFILES_ACTIVE=api    ./mvnw spring-boot:run

./scripts/verify.sh           # build + tests on H2 (MODE=MySQL), no infra needed;
                              # locates a JDK 21 automatically if JAVA_HOME is unset
```

One image serves both roles:

```sh
docker build -t ingest .
docker run -e SPRING_PROFILES_ACTIVE=worker -e DB_URL=... -e NATS_URL=... ingest
docker run -e SPRING_PROFILES_ACTIVE=api    -e DB_URL=... -p 8080:8080     ingest
```

## Extending

**Add a namespace** — implement `NamespacePolicy` as a `@Component` in
`namespace/policies/`, then list its key in `APP_NAMESPACES_ENABLED` wherever it
should be live. Nothing else changes: the pipeline, ledger, table, and API pick it up.

**Add a source** — add a `SourceConsumer` implementation under `worker/nats/`
(subject → event type, header/body → namespace via a resolver in `worker/source/`)
and its stream config under `app.nats.sources.<key>` in `application-worker.yml`.
No runner change: `SourceRegistry` subscribes every enabled source and fails fast
when config and beans disagree. All sources reuse `CommonPayloadReader`,
`IngestPipeline`, and the same tables.

## Layout

```
src/main/java/com/example/ingest/
  namespace/   NamespacePolicy SPI, NamespaceRegistry, policies/ (one class per namespace)
  record/      IngestedRecord + repository            — shared by worker and API
  worker/      IngestPipeline, ledger/, source/ (resolvers), nats/ (consumers)  @Profile("worker")
  api/         NamespaceController                                              @Profile("api")
src/main/resources/db/migration/   Flyway schema — the shared DB contract
```
