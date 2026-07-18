package com.example.ingest.stress;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.record.IngestedRecordRepository;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.worker.composition.CompositionPart;
import com.example.ingest.worker.composition.CompositionPartRepository;
import com.example.ingest.worker.composition.CompositionState;
import com.example.ingest.worker.composition.CompositionStateRepository;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.composition.CompositionStatus;
import com.example.ingest.worker.composition.CompositionSweeper;
import com.example.ingest.worker.ledger.IngestLedgerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Thread-level stress tests for the composition stage's concurrency
 * invariants. Excluded from the default build; run via scripts/stress.sh
 * (tier 1 = H2 in-JVM, tier 2 = real MariaDB via env override).
 *
 * These tests simulate the consumer's nak/redelivery loop in
 * ingestWithRedelivery: any DataAccessException (PK/unique collision, lock
 * timeout) is what production answers with nak(), so the test retries the
 * same call — asserting that the SYSTEM converges, not that no race ever
 * fires.
 */
@Tag("stress")
@SpringBootTest(properties = {
        "app.namespaces.enabled=alpha,beta",
        "spring.datasource.hikari.maximum-pool-size=24"
})
@Timeout(120)
class CompositionStressTest {

    private static final int ROUNDS = Integer.getInteger("stress.rounds", 200);
    private static final int THREADS = Integer.getInteger("stress.threads", 16);
    private static final int MAX_REDELIVERIES = 50;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(THREADS);

    @Autowired
    private CompositionStage stage;

    @Autowired
    private CompositionSweeper sweeper;

    @Autowired
    private CompositionStateRepository states;

    @Autowired
    private CompositionPartRepository parts;

    @Autowired
    private IngestedRecordRepository records;

    @Autowired
    private IngestLedgerRepository ledger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterAll
    static void shutdownPool() {
        POOL.shutdownNow();
    }

    @BeforeEach
    void cleanDatabase() {
        parts.deleteAll();
        states.deleteAll();
        records.deleteAll();
        ledger.deleteAll();
    }

    private CommonEnvelope alphaPart(String eventType, String correlationId) {
        try {
            return new CommonEnvelope(SourceKey.SOURCE_A, eventType,
                    eventType + "-" + correlationId, Instant.parse("2026-01-01T00:00:00Z"),
                    objectMapper.readTree("{\"correlationId\":\"" + correlationId
                            + "\",\"data\":{\"" + eventType.charAt(0) + "\":1}}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The consumer's semantics: DataAccessException -> nak -> redelivery of the same message. */
    private IngestResult ingestWithRedelivery(String namespaceKey, CommonEnvelope envelope) {
        for (int attempt = 0; ; attempt++) {
            try {
                return stage.ingest(namespaceKey, envelope);
            } catch (DataAccessException e) {
                if (attempt >= MAX_REDELIVERIES) {
                    throw new AssertionError("no convergence after " + MAX_REDELIVERIES
                            + " redeliveries for " + envelope.dedupKey(), e);
                }
                try {
                    Thread.sleep(2);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            }
        }
    }

    /**
     * THE core race: both halves of every correlation arrive at the same
     * instant (this also exercises the concurrent-first-part PK race,
     * because neither has a state row yet). Invariant: exactly one SAVED per
     * correlation, zero stuck-PENDING states, exactly one record each.
     */
    @Test
    void bothPartsArrivingSimultaneouslyComposeExactlyOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            String correlationId = "race-" + round;
            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<IngestResult> x = POOL.submit(() -> {
                barrier.await();
                return ingestWithRedelivery("alpha", alphaPart("x.ready", correlationId));
            });
            Future<IngestResult> y = POOL.submit(() -> {
                barrier.await();
                return ingestWithRedelivery("alpha", alphaPart("y.ready", correlationId));
            });
            List<IngestResult> outcomes = List.of(x.get(), y.get());

            assertThat(outcomes).filteredOn(result -> result == IngestResult.SAVED)
                    .as("round %s outcomes: %s", correlationId, outcomes)
                    .hasSize(1);
        }

        assertThat(records.count()).isEqualTo(ROUNDS);
        assertThat(states.countByStatus(CompositionStatus.PENDING)).isZero();
        assertThat(states.countByStatus(CompositionStatus.COMPOSED)).isEqualTo(ROUNDS);
    }

    /**
     * Duplicate deliveries of the SAME part from many threads: the
     * (correlation_key, part_key) unique constraint must collapse them to
     * one row, and the flow must still compose exactly once afterwards.
     */
    @Test
    void duplicatePartFloodCollapsesToOneRow() throws Exception {
        String correlationId = "dup-1";
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<IngestResult>> floods = POOL.invokeAll(java.util.Collections.nCopies(THREADS,
                () -> {
                    barrier.await();
                    return ingestWithRedelivery("alpha", alphaPart("x.ready", correlationId));
                }));
        for (Future<IngestResult> flood : floods) {
            assertThat(flood.get()).isIn(IngestResult.BUFFERED, IngestResult.DUPLICATE);
        }
        assertThat(parts.findByCorrelationKey("alpha:" + correlationId)).hasSize(1);

        assertThat(ingestWithRedelivery("alpha", alphaPart("y.ready", correlationId)))
                .isEqualTo(IngestResult.SAVED);
        assertThat(records.count()).isEqualTo(1);
    }

    /**
     * Sweeper racing the final part on an already-overdue correlation: the
     * outcome must be exactly one of (COMPOSED and one record) or
     * (EXPIRED and no record) — never both, never neither.
     */
    @Test
    void sweeperVersusFinalPartConvergesToExactlyOneOutcome() throws Exception {
        int composed = 0;
        for (int round = 0; round < ROUNDS; round++) {
            String correlationId = "exp-" + round;
            String key = "alpha:" + correlationId;
            // composedEventType null: this test counts records, and an expiry
            // marker would blur the one-outcome-per-round assertion.
            states.save(new CompositionState(key, "alpha", CompositionStatus.PENDING,
                    "x.ready,y.ready", null, Instant.now().minusSeconds(1), Instant.now()));
            parts.save(new CompositionPart(key, "x.ready", "SOURCE_A", "x.ready",
                    "{\"correlationId\":\"" + correlationId + "\",\"data\":{\"x\":1}}",
                    Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));

            CyclicBarrier barrier = new CyclicBarrier(2);
            Future<?> sweep = POOL.submit(() -> {
                barrier.await();
                sweeper.sweep();
                return null;
            });
            Future<IngestResult> y = POOL.submit(() -> {
                barrier.await();
                return ingestWithRedelivery("alpha", alphaPart("y.ready", correlationId));
            });
            sweep.get();
            IngestResult outcome = y.get();

            CompositionStatus status = states.findById(key).orElseThrow().getStatus();
            long recordCount = records.findAll().stream()
                    .filter(record -> key.equals(record.getDedupKey())).count();
            if (status == CompositionStatus.COMPOSED) {
                assertThat(outcome).as(key).isEqualTo(IngestResult.SAVED);
                assertThat(recordCount).as(key).isEqualTo(1);
                composed++;
            } else {
                assertThat(status).as(key).isEqualTo(CompositionStatus.EXPIRED);
                assertThat(outcome).as(key).isEqualTo(IngestResult.DUPLICATE);
                assertThat(recordCount).as(key).isZero();
            }
        }
        assertThat(records.count()).isEqualTo(composed);
        // Sanity that the race is real: the final-part path must win at least
        // once. The upper bound is deliberately ROUNDS (not ROUNDS - 1):
        // confirmed on MariaDB that the sweeper — which must scan before
        // taking the row lock — systematically loses to ingest's direct
        // locking read (0 sweeper wins in 1000 solo rounds), so expired == 0
        // is the expected outcome there; H2 exercises both branches.
        assertThat(composed).isBetween(1, ROUNDS);
    }

    /**
     * Ledger exactly-once under concurrent redelivery of one passthrough
     * message (no composition involved): one SAVED, the rest DUPLICATE,
     * one record, one ledger row.
     */
    @Test
    void concurrentRedeliveryOfOnePassthroughMessageSavesOnce() throws Exception {
        CommonEnvelope ready;
        try {
            ready = new CommonEnvelope(SourceKey.SOURCE_B, "ready", "same-key",
                    Instant.parse("2026-01-01T00:00:00Z"),
                    objectMapper.readTree("{\"attributes\":[{\"name\":\"s\",\"value\":\"ok\"}]}"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<IngestResult>> deliveries = POOL.invokeAll(java.util.Collections.nCopies(THREADS,
                () -> {
                    barrier.await();
                    return ingestWithRedelivery("beta", ready);
                }));
        long saved = 0;
        for (Future<IngestResult> delivery : deliveries) {
            if (delivery.get() == IngestResult.SAVED) {
                saved++;
            } else {
                assertThat(delivery.get()).isEqualTo(IngestResult.DUPLICATE);
            }
        }
        assertThat(saved).isEqualTo(1);
        assertThat(records.count()).isEqualTo(1);
        assertThat(ledger.count()).isEqualTo(1);
    }
}
