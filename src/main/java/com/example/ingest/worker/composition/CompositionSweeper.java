package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestPipeline;
import com.example.ingest.worker.IngestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Housekeeping for the composition buffer (decision D3): overdue PENDING
 * correlations become EXPIRED — no partial ingest — and terminal rows past
 * retention are deleted together with their parts.
 *
 * <p>An expiry is also recorded as data, not just a log line: the arrived
 * parts are ingested through the normal pipeline as a
 * {@code <composedEventType>.expired} marker (body =
 * {@code {"parts": {...}, "missing": [...]}}), so ops can see what expired —
 * and with which parts — from the namespace API instead of the DB. The marker
 * is best-effort in its own transaction after the expiry commits: a namespace
 * policy that does not handle the marker type only loses the marker, never
 * the expiry.
 *
 * <p>Each expiry runs in its own transaction whose first read takes the same
 * row lock {@code CompositionStage.ingest} uses, so a sweeper-vs-final-part
 * race serializes as a lock wait instead of failing the whole batch (MariaDB
 * rejects reads of rows changed after the transaction snapshot with error
 * 1020, so the lock must be acquired before any other read of the row).
 */
// Gateway boots without a database; every other context (worker, api, tests) loads this bean.
@Profile("!gateway")
@Component
public class CompositionSweeper {

    private static final Logger log = LoggerFactory.getLogger(CompositionSweeper.class);

    private final CompositionStateRepository states;
    private final CompositionPartRepository parts;
    private final CompositionMetrics metrics;
    private final CompositionProperties properties;
    private final IngestPipeline pipeline;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;

    public CompositionSweeper(CompositionStateRepository states,
                              CompositionPartRepository parts,
                              CompositionMetrics metrics,
                              CompositionProperties properties,
                              IngestPipeline pipeline,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.states = states;
        this.parts = parts;
        this.metrics = metrics;
        this.properties = properties;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.composition.sweep-interval:60s}")
    public void sweep() {
        Instant now = Instant.now();

        List<CompositionState> overdue =
                states.findByStatusAndDeadlineAtBefore(CompositionStatus.PENDING, now);
        for (CompositionState state : overdue) {
            try {
                if (Boolean.TRUE.equals(transactions.execute(tx -> expire(state)))) {
                    recordExpiryMarker(state);
                }
            } catch (DataAccessException e) {
                // Lost a race with a concurrent ingest of the same correlation
                // (e.g. picked as a deadlock victim): the row is still PENDING
                // or was just composed — leave it for the next cycle instead
                // of failing the rest of the batch.
                log.debug("sweep of {} lost a race, retrying next cycle",
                        state.getCorrelationKey(), e);
            }
        }

        transactions.executeWithoutResult(tx -> {
            Instant cutoff = now.minus(properties.retention());
            int deletedStates = states.deleteTerminalOlderThan(CompositionStatus.PENDING, cutoff);
            int deletedParts = parts.deleteOrphansOlderThan(cutoff);
            if (deletedStates > 0 || deletedParts > 0) {
                log.info("composition retention removed {} states and {} parts", deletedStates, deletedParts);
            }
        });
    }

    private boolean expire(CompositionState state) {
        // Serialize with CompositionStage.ingest on the same row lock, then
        // re-check: the ingest we waited on may have composed it meanwhile.
        CompositionState locked = states
                .findWithLockingByCorrelationKey(state.getCorrelationKey()).orElse(null);
        if (locked == null || locked.getStatus() != CompositionStatus.PENDING) {
            return false;
        }
        if (states.markExpired(state.getCorrelationKey(),
                CompositionStatus.EXPIRED, CompositionStatus.PENDING) != 1) {
            return false;
        }
        metrics.expired(state.getNamespaceKey());
        log.warn("composition {} expired with incomplete parts (required: {})",
                state.getCorrelationKey(), state.getRequiredParts());
        return true;
    }

    /**
     * Best-effort, outside the expiry transaction: the expiry must never be
     * rolled back because the marker could not be ingested.
     */
    private void recordExpiryMarker(CompositionState state) {
        if (state.getComposedEventType() == null) {
            return; // row predates the composed_event_type column
        }
        try {
            List<CompositionPart> arrived = parts.findByCorrelationKey(state.getCorrelationKey());
            if (arrived.isEmpty()) {
                return;
            }
            IngestResult result = pipeline.ingest(state.getNamespaceKey(),
                    markerEnvelope(state, arrived));
            log.info("recorded expiry marker for composition {} -> {}",
                    state.getCorrelationKey(), result);
        } catch (RuntimeException e) {
            log.warn("composition {} expired but its marker was not recorded: {}",
                    state.getCorrelationKey(), e.getMessage());
        }
    }

    private CommonEnvelope markerEnvelope(CompositionState state, List<CompositionPart> arrived) {
        ObjectNode partsNode = JsonNodeFactory.instance.objectNode();
        for (CompositionPart part : arrived) {
            partsNode.set(part.getPartKey(), readPayload(part));
        }
        Set<String> arrivedKeys = arrived.stream()
                .map(CompositionPart::getPartKey).collect(Collectors.toSet());
        ArrayNode missing = JsonNodeFactory.instance.arrayNode();
        for (String required : state.getRequiredParts().split(",")) {
            if (!arrivedKeys.contains(required)) {
                missing.add(required);
            }
        }
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        body.set("parts", partsNode);
        body.set("missing", missing);
        return new CommonEnvelope(
                SourceKey.valueOf(CompositionPart.firstArrived(arrived).getSourceKey()),
                state.getComposedEventType() + ".expired",
                state.getCorrelationKey() + ":expired",
                Instant.now(),
                body);
    }

    private com.fasterxml.jackson.databind.JsonNode readPayload(CompositionPart part) {
        try {
            return objectMapper.readTree(part.getPayload());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "stored composition part " + part.getId() + " is not valid JSON", e);
        }
    }
}
