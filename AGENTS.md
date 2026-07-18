# AGENTS.md — instructions for coding agents

Read this whole file before changing anything. Follow it exactly.
Architecture background is in `README.md`. This file tells you HOW to work here.

## The one command that matters

```sh
./scripts/verify.sh
```

Run it before you start (must be green) and after every change.
A task is finished ONLY when it prints `BUILD SUCCESS`. If it prints
`BUILD FAILURE`, your work is not done — read the first `[ERROR]` line and fix it.
Never commit on a red build. Never skip tests to make the build pass.

Run a single test class while iterating:

```sh
./scripts/verify.sh -Dtest=AlphaNamespacePolicyTest -DfailIfNoTests=false
```

Tests run on in-memory H2 — no Docker, no database, no NATS needed.

## Hard rules (do not break these, do not "improve" them)

1. **Never edit or delete an existing file in `src/main/resources/db/migration/`.**
   Applied Flyway migrations are immutable. To change the schema, ADD a new file:
   `V2__short_description.sql`, then `V3__...`, etc. (next unused number).
2. **Schema is owned by Flyway, not JPA.** `spring.jpa.hibernate.ddl-auto` stays
   `validate` in every yml. Never change it to `update`, `create`, or `create-drop`.
3. If you change a migration/entity, change BOTH: the new `V<n>__*.sql` AND the
   matching `@Entity` class. They must describe the same columns or startup fails.
4. Migration SQL must work on BOTH MariaDB and H2 in MySQL mode (tests use H2).
   Stick to: `BIGINT AUTO_INCREMENT`, `VARCHAR(n)`, `TEXT`, `DATETIME(6)`,
   plain `CREATE TABLE / CREATE INDEX / ALTER TABLE ADD COLUMN`.
   Do not use JSON columns, generated columns, or MariaDB-only syntax.
5. **No cross-namespace reads.** Every API query filters by one `namespace_key`.
   Do not add endpoints or repository methods that scan all namespaces.
6. Worker beans stay `@Profile("worker")`, controllers stay `@Profile("api")`,
   gateway beans stay `@Profile("gateway")`. Shared code (`namespace/`,
   `record/`) has NO profile annotation. One deliberate exception:
   `IngestPipeline` is `@Profile("!gateway")` — the gateway boots without a
   database.
7. Message handling semantics live ONLY in `BaseSourceConsumer.onMessage`
   (final — never override it, never add a second `onMessage` path):
   - handled fine (any `IngestResult`) → `ack()`
   - `IllegalArgumentException` (malformed, poison) → `term()`
   - any other exception → `nak()`
   Metrics wrap these paths (poison counter on term) — keep the counters
   if you ever touch the base class. Concrete consumers only implement
   `source()` and `resolveNamespace(...)`.
8. Do not add dependencies to `pom.xml` unless the task explicitly requires it.
9. Do not rename packages, move files, or reformat code you were not asked to touch.
   Make the smallest change that completes the task.
10. Do not create README-style docs, TODO files, or example files nobody asked for.

## Workflow for every task

1. Run `./scripts/verify.sh` — confirm the baseline is green.
2. Find the relevant recipe below. If one matches, follow it step by step.
3. Write or update the test FIRST, watch it fail, then implement.
4. Run `./scripts/verify.sh` — all tests green.
5. Commit with a message like `feat: ...` / `fix: ...` / `chore: ...`
   (one line, imperative, no period). Do not push unless asked.

## File map

```
src/main/java/com/example/ingest/
  namespace/            SHARED. NamespacePolicy (SPI), NamespaceRegistry,
                        NamespaceProperties, CommonEnvelope, SourceKey,
                        CommonPayload + CommonPayloadReader, MessageHeaders
  namespace/policies/   One class per namespace (AlphaNamespacePolicy, ...)
  record/               SHARED. IngestedRecord entity + repository (the main table)
  worker/               WORKER ONLY. IngestPipeline, IngestResult,
                        IngestMetrics (source/namespace/result tag convention)
  worker/composition/   CompositionStage + CompositionPolicy (SPI), state/part
                        entities, sweeper; plans/ = one class per flow
  worker/ledger/        Dedup ledger entity + repository
  worker/source/        Per-source namespace resolvers
  worker/nats/          NatsConfig, NatsProperties, SourceProperties,
                        SourceConsumer (SPI), BaseSourceConsumer (owns
                        ack/term/nak — hard rule 7), SourceRegistry,
                        NatsSubscriptionRunner, SourceAConsumer, SourceBConsumer
  api/                  API ONLY. NamespaceController, RecordResponse
  gateway/              GATEWAY ONLY. GatewayController + GatewayAuthenticator (SPI),
                        EventPublisher -> NATS, ftp/ (FileSourceClient SPI + FtpPoller)
src/main/resources/
  application.yml               shared config (DB, enabled namespaces)
  application-worker.yml        enabled sources + NATS streams/subjects/durables
  application-api.yml           server port
  application-gateway.yml       gateway port/token/FTP; excludes DB autoconfig
  db/migration/V1__init.sql     schema (append V2, V3, ... — never edit V1)
src/test/java/...               mirrors main; src/test/resources/application.yml = H2
src/test/resources/golden/<ns>/ golden contract files, one dir per namespace
deploy/k8s/                     kustomize: base/{worker,gateway} + one overlay per
                                route + monitoring; no build impact. Rules and the
                                add-a-route recipe live in deploy/k8s/README.md
```

Data flow: NATS message → source consumer → resolver picks namespace →
`CompositionStage.ingest()` (passthrough unless a `CompositionPolicy` claims the
event; claimed events buffer until all parts arrive, then one composed envelope
continues) → `IngestPipeline.ingest()` → ledger dedup → `NamespacePolicy.parse()`
→ `ingested_record` row. The API reads that same table, always scoped to one namespace.
A buffered part returns `IngestResult.BUFFERED` — still an ACK (hard rule 7).
The gateway (profile `gateway`) bridges HTTP posts and FTP files into
`src-http.events.>` / `src-ftp.events.>` — it publishes only, never resolves
namespaces, never touches the DB.

---

## Recipe: add a new namespace (e.g. `gamma`)

Touch exactly 3 things. No pipeline, controller, or schema changes needed.

**1. Test** — `src/test/java/com/example/ingest/namespace/policies/GammaNamespacePolicyTest.java`.
Copy `AlphaNamespacePolicyTest.java` and adapt: one test for the happy-path parse
shape, one asserting `IllegalArgumentException` on a malformed body.

**2. Policy** — `src/main/java/com/example/ingest/namespace/policies/GammaNamespacePolicy.java`:

```java
package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class GammaNamespacePolicy implements NamespacePolicy {

    public static final String KEY = "gamma";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public JsonNode parse(CommonEnvelope envelope) {
        // Namespace-specific logic: turn envelope.body() into the record payload.
        // Throw IllegalArgumentException if the body does not match this namespace's shape.
        JsonNode data = envelope.body().path("data");
        if (!data.isObject()) {
            throw new IllegalArgumentException("gamma payload requires an object 'data' field");
        }
        return data;
    }
}
```

**3. Golden contract** — create `src/test/resources/golden/gamma/` with at least
`happy.input.json` + `happy.expected.json` and one `<case>.invalid.json`.
`NamespaceGoldenTest` fails until the directory exists, and only ever reads
YOUR namespace's directory — other namespaces' files must not change.

**4. Enable it** — add the key to the `enabled` list in
`src/main/resources/application.yml` default AND `src/test/resources/application.yml`
if tests need it. Deployments control the real list via `APP_NAMESPACES_ENABLED`.

If source A should route messages to the new namespace, also add the
`(category, region)` mapping in `worker/source/SourceANamespaceResolver.java`
and extend `SourceANamespaceResolverTest`.

## Recipe: add a composition flow (N events -> one record)

1. **Test first** — copy `AlphaReadyCompositionPolicyTest`: plan fields for each
   claimed event type, empty for everything else, `IllegalArgumentException`
   when the correlation field is missing.
2. **Plan class** — `worker/composition/plans/<Ns><Flow>CompositionPolicy.java`
   implementing `CompositionPolicy`. Claim ONLY your namespace + event types
   (flows must be disjoint); return correlationKey/partKey/requiredParts/
   timeout/composedEventType.
3. **Composed parse** — the owning `NamespacePolicy` handles the composed event
   type (body = `{partKey: partBody, ...}`); the event type string is duplicated
   there on purpose — namespace code must not import worker code (ArchUnit).
4. **Golden files** — add the composed happy + invalid cases under
   `golden/<your-namespace>/` only.
No stage, schema, or consumer changes — `CompositionStage` picks up the plan bean.

## Recipe: change the schema (add a column)

**1. New migration** — `src/main/resources/db/migration/V2__add_<column>.sql`
(or the next free number — check the directory first):

```sql
ALTER TABLE ingested_record ADD COLUMN trace_id VARCHAR(64) NULL;
```

New columns on existing tables must be `NULL`able or have a `DEFAULT` —
existing rows must survive the migration.

**2. Entity** — add the matching field + getter to
`record/IngestedRecord.java` (`@Column(name = "trace_id")`), and thread it
through the `of(...)` factory and its callers if it is written at ingest time.

**3. Verify** — `./scripts/verify.sh`. The repository tests boot Flyway against H2;
a mismatch between SQL and entity fails here. That failure means YOUR step 1 and
step 2 disagree — fix them; do not touch `ddl-auto`.

## Recipe: add an API endpoint

- Add the method to `api/NamespaceController.java` (or a new `@RestController`
  in `api/` with `@Profile("api")`).
- Path must stay under `/api/namespaces/{namespaceKey}/...`, and the handler must
  start with the same registry check the existing `records` endpoint uses
  (404 via `ResponseStatusException` for unknown/disabled namespaces).
- Repository methods must take `namespaceKey` as a parameter
  (`findByNamespaceKeyAnd...`). Never query without it (hard rule 5).
- Test in `src/test/java/com/example/ingest/api/` — copy the style of
  `NamespaceControllerIntegrationTest` (`@SpringBootTest` + MockMvc,
  `@ActiveProfiles("api")`): one happy-path test, one 404 test.

## Recipe: add a new NATS source (rare, bigger job)

Follow the SOURCE_A trail end to end; every step mirrors an existing class:

1. Add the enum constant in `namespace/SourceKey.java` with its kebab-case
   config key AND its subject prefix, e.g.
   `SOURCE_C("source-c", "src-c.events.")`. The prefix is the single source
   of truth — `SourceRegistry` rejects config whose subject disagrees.
2. Add a source block under `app.nats.sources.source-c` in
   `application-worker.yml` (stream, durable, and subject `src-c.events.>` —
   must be the enum prefix + `>`), and add `source-c` to the
   `APP_SOURCES_ENABLED` default in the same file.
3. Write a resolver in `worker/source/` deciding the namespace from
   header/body per the upstream contract (+ unit test). One resolver class
   per source, even if it is trivial — copy `SourceBNamespaceResolver` for
   the header-declared case, `SourceANamespaceResolver` for derived logic.
4. Consumer — `worker/nats/SourceCConsumer.java`, extending
   `BaseSourceConsumer`. The base owns `onMessage` (ack/term/nak, hard
   rule 7) and the envelope building; you implement ONLY the two methods
   below (+ test, copy `SourceAConsumerTest`):

```java
package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.namespace.MessageHeaders;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.source.SourceCNamespaceResolver;
import io.nats.client.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source C: subjects {@code src-c.events.<eventType>}; describe here how
 * this source names its namespace.
 */
@Component
@Profile("worker")
public class SourceCConsumer extends BaseSourceConsumer {

    private final SourceCNamespaceResolver namespaceResolver;

    public SourceCConsumer(CommonPayloadReader payloadReader,
                           SourceCNamespaceResolver namespaceResolver,
                           CompositionStage compositionStage,
                           IngestMetrics metrics) {
        super(payloadReader, compositionStage, metrics);
        this.namespaceResolver = namespaceResolver;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_C;
    }

    @Override
    protected String resolveNamespace(Message message, CommonPayload payload) {
        // Adapt to this source's contract; header(...) is a null-safe helper.
        return namespaceResolver.resolve(header(message, MessageHeaders.NAMESPACE));
    }
}
```

5. There is NO runner or base-class change: `SourceRegistry` discovers the
   consumer and subscribes it if the key is enabled. It fails at startup if
   config and beans disagree.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Unable to locate a Java Runtime` | Use `./scripts/verify.sh`, not bare `./mvnw` |
| Flyway `Validate failed: checksum mismatch` | You edited an existing migration. Revert it; put the change in a NEW `V<n>__*.sql` |
| Hibernate `Schema-validation: missing column/table` | Entity and migration disagree — fix the new migration or the entity, never `ddl-auto` |
| `Enabled namespaces without a NamespacePolicy implementation` | A key in `enabled` config has no `@Component` policy — add the class or remove the key |
| Test can't find bean of a `@Profile("worker")`/`("api")` class | The test's active profile doesn't include it — see how existing integration tests set profiles |
| Flyway ignores your new migration in tests | Wrong filename. Must match `V<number>__<name>.sql` with TWO underscores |

## Definition of done — check every box before you say you are finished

- [ ] `./scripts/verify.sh` prints `BUILD SUCCESS` (all tests, no skips)
- [ ] New behavior has a test that fails without your change
- [ ] No edits to existing files under `db/migration/`
- [ ] `ddl-auto` still `validate` everywhere; no new `pom.xml` deps (unless asked)
- [ ] Diff contains only files the task required
- [ ] Commit message is one imperative line: `feat: ...` / `fix: ...` / `chore: ...`
