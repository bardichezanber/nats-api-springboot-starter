# nats-api-springboot-starter

Single-repo Spring Boot template: a JetStream NATS **ingest worker**, a **read API**,
and an **ingest gateway** (HTTP + FTP bridged into NATS) share one codebase, one
Docker image, and one database schema. The role is chosen at deploy time with
`SPRING_PROFILES_ACTIVE=worker|api|gateway`.

## Architecture

Ingest has two independent dimensions:

```
Sources     — four sources: two native NATS (A / B) and two bridged through the
              gateway (HTTP / FTP); each has its own event types and its own
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
| **HTTP** | declared by the caller, forwarded by the gateway in `X-Namespace` |
| **FTP** | declared per file line, forwarded by the gateway in `X-Namespace` |

### Shared database schema

Both roles run the same Flyway migrations (`src/main/resources/db/migration/`).

| Table | Used by | Purpose |
|---|---|---|
| `ingested_record` | worker (write), API (read) | The shared read model: every source and event type lands here, normalized by its namespace policy. `UNIQUE(source_key, dedup_key)`; indexed on `(namespace_key, occurred_at DESC)` because reads are always scoped to one namespace. |
| `ingest_ledger` | worker only | Dedup ledger keyed by `source_key + ":" + dedup_key`. The ledger entry and the record commit in the same transaction; the primary key is the backstop under concurrent redelivery. A sweeper prunes entries older than `APP_LEDGER_RETENTION` (default 30d) — keep that retention longer than any stream's replay window, or replays of swept messages get stuck NAKing on the record unique constraint. |

JPA runs with `ddl-auto: validate` — the schema is owned by Flyway, entities must match.

### Namespaces are code, enabled by config

Each namespace is a hand-written `NamespacePolicy` implementation (see
`namespace/policies/`) — namespaces may differ in parse *logic*, not just parameters.
`APP_NAMESPACES_ENABLED` is a **switch**: a namespace not listed is business-wise not
collected — the worker ACKs and drops its messages, and the API returns 404 for it.
`NamespaceRegistry` fails fast at startup if an enabled key has no policy.

**The drop is not final while the stream retains.** ACK only advances the
durable's cursor; on limits-based streams (this project's kind) the messages
stay until `MaxAge`/`MaxBytes`. Enabling a namespace late therefore has a
regret window = the stream's retention: to recover history, run a one-off
backfill consumer on the source stream (`DeliverPolicy.ByStartTime` or
`All`) and feed it through the worker — already-ingested messages dedup as
DUPLICATE. Two rules when provisioning streams out-of-band for production
(dev auto-creates them with **no limits**): set `MaxAge` to the longest
"collect later" window the business may want, and keep any backfill window
inside `APP_LEDGER_RETENTION` — replaying a message whose ledger entry was
already swept NAK-loops on the record unique constraint.

## API

```
GET /api/namespaces                          -> enabled namespace keys
GET /api/namespaces/{key}/records?page=&size= -> page of records, newest first
GET /api/namespaces/{key}/records/window?size=&occurredBefore=&idBefore=
                                             -> keyset page, newest first
```

There are no cross-namespace reads. `records` is classic offset paging;
`window` is the keyset variant that stays cheap at any depth: call it without
a cursor, then keep passing the response's `nextOccurredBefore`/`nextIdBefore`
back until `records` comes back empty.

## Message handling semantics

- **SAVED / DUPLICATE / NAMESPACE_DISABLED / UNKNOWN_NAMESPACE** → `ack()`
- Malformed payload (`IllegalArgumentException`) → `term()` (poison, redelivery cannot fix it)
- Any other failure → `nak()` (redelivered)

Malformed messages also increment the `ingest.poison` counter.

## Composition (N events -> one record)

Some flows only make sense once several events arrived. A `CompositionPolicy`
(one class per flow, `worker/composition/plans/`) claims (namespace, event)
pairs and maps them to a correlation — claims must be disjoint, and
`CompositionStage` refuses to start when two flows claim the same pair; `CompositionStage` buffers claimed parts
(`IngestResult.BUFFERED` → still ACK — the DB owns the data from then on) and
the **last part's transaction** builds one composed event
(`body = {partKey: partBody}`, `dedupKey = <namespace>:<correlationKey>`) that
runs through the normal pipeline. Unclaimed events pass through untouched.

Example: alpha needs `x.ready` **and** `y.ready` (same `correlationId`) before
one `ready.composed` event is parsed; beta ingests a single `ready` event
directly because no policy claims it.

State machine: `PENDING -> COMPOSED | EXPIRED`, nothing else. Overdue
correlations are **discarded** by the sweeper — no partial ingest — but the
expiry is recorded as data: the arrived parts are ingested as a
`<composedEventType>.expired` marker (body `{"parts": {...}, "missing":
[...]}`, dedupKey `<correlation>:expired`), queryable through the namespace
API. The marker is best-effort: it needs the owning `NamespacePolicy` to
handle the type (see `AlphaNamespacePolicy`); without that branch the flow
expires with only the metric + warn log. Expired/composed rows are kept for
`APP_COMPOSITION_RETENTION` (default 7d) for troubleshooting.
Redrive (runbook): within retention, set the state row back
(`UPDATE composition_state SET status='PENDING', deadline_at=<new deadline>
WHERE correlation_key=...`) and re-send the missing part — the flow then
composes normally; the marker record stays as the audit trail. Concurrency: parts of one correlation
serialize on a pessimistic state-row lock; the state PK, the
`(correlation_key, part_key)` unique constraint, and the guarded
COMPOSED-update are backstops, so races surface as DUPLICATE/redelivery,
never as lost or doubled events.
Concurrency invariants are covered by `./scripts/stress.sh` (see AGENTS.md).

## Scaling & monitoring

Workers use durable **pull** consumers: every replica fetches from the same
durable, so per-route deployments (`APP_SOURCES_ENABLED=source-a`) scale
horizontally on consumer lag (e.g. KEDA's nats-jetstream scaler).
Upgrading an existing environment: the old push consumers must be deleted
(`nats consumer rm <stream> <durable>`) before starting this version.

`/actuator/prometheus` exposes Micrometer metrics tagged `source` /
`namespace` / `result`: `ingest_messages_total`, `ingest_handle_seconds`,
`ingest_poison_total`, `composition_active`, `composition_completed_total`,
`composition_expired_total` (worker) and `gateway_publish_total`,
`gateway_ftp_files_total`, `gateway_ftp_scan_lag_seconds` (gateway).

## Gateway role (HTTP + FTP sources)

`SPRING_PROFILES_ACTIVE=gateway` runs a DB-free bridge that publishes into
JetStream; the worker consumes the result like any other source.

**HTTP**: `POST /gateway/events/{eventType}` with `Authorization: Bearer
$APP_GATEWAY_TOKEN`, an `X-Namespace` header, and a common-payload JSON body
(`eventId`, `occurredAt`, ...). Returns `202 {"dedupKey": ...}`. Auth is a
static token placeholder — swap `StaticTokenGatewayAuthenticator` for the
corporate `GatewayAuthenticator` implementation (see `TODO(auth)`).

**FTP**: a poller scans `inbox/` on the configured FTP server. Files are
NDJSON — each line `{"eventType": ..., "namespace": ..., "eventId": ...,
"occurredAt": ..., ...}`. Lifecycle: `inbox/ -> processing/ (rename = the
multi-instance lock) -> archive/`, or `error/` if any line is invalid (valid
lines still publish; re-dropping a fixed file is safe — `Nats-Msg-Id` and the
worker ledger dedup replays). A publish failure leaves the file in
`processing/` for manual redrive. Protocols other than classic FTP are one
`FileSourceClient` implementation away (`FtpFileSourceClient` is the FTP one).

## Configuration

| Env var | Default | Meaning |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | `worker` or `api` |
| `APP_NAMESPACES_ENABLED` | `alpha,beta` | Enabled namespace keys (switch semantics) |
| `APP_SOURCES_ENABLED` | `source-a,source-b` | Sources this worker subscribes (switch semantics, per-route deployments) |
| `DB_URL` | `jdbc:mariadb://localhost:3306/ingest` | JDBC URL (MariaDB) |
| `DB_USERNAME` / `DB_PASSWORD` | `ingest` / `ingest` | DB credentials |
| `NATS_URL` | `nats://localhost:4222` | NATS server (worker and gateway) |
| `APP_GATEWAY_TOKEN` | — | Gateway bearer token (unset = all requests rejected) |
| `APP_FTP_ENABLED` | `false` | Enable the gateway FTP poller |
| `APP_FTP_HOST` / `APP_FTP_PORT` | `localhost` / `21` | FTP server (gateway only) |
| `APP_FTP_USERNAME` / `APP_FTP_PASSWORD` | — | FTP credentials |
| `APP_FTP_INBOX_DIR` (+ `PROCESSING`/`ARCHIVE`/`ERROR`) | `/inbox` ... | FTP directories |
| `APP_FTP_POLL_INTERVAL` | `30s` | FTP scan interval |
| `APP_COMPOSITION_RETENTION` | `7d` | Keep expired/composed correlations this long |
| `APP_COMPOSITION_SWEEP_INTERVAL` | `60s` | Composition sweeper cadence (worker only) |
| `APP_LEDGER_RETENTION` | `30d` | Keep dedup ledger entries this long (must exceed every stream's replay window) |
| `APP_LEDGER_SWEEP_INTERVAL` | `1h` | Ledger sweeper cadence (worker only) |
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
