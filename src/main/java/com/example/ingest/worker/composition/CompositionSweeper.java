package com.example.ingest.worker.composition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Housekeeping for the composition buffer (decision D3): overdue PENDING
 * correlations become EXPIRED — discarded with a metric and a warn log, no
 * partial ingest — and terminal rows past retention are deleted together
 * with their parts.
 *
 * Each expiry runs in its own transaction whose first read takes the same
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
    private final TransactionTemplate transactions;

    public CompositionSweeper(CompositionStateRepository states,
                              CompositionPartRepository parts,
                              CompositionMetrics metrics,
                              CompositionProperties properties,
                              PlatformTransactionManager transactionManager) {
        this.states = states;
        this.parts = parts;
        this.metrics = metrics;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${app.composition.sweep-interval:60s}")
    public void sweep() {
        Instant now = Instant.now();

        List<CompositionState> overdue =
                states.findByStatusAndDeadlineAtBefore(CompositionStatus.PENDING, now);
        for (CompositionState state : overdue) {
            try {
                transactions.executeWithoutResult(tx -> expire(state));
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

    private void expire(CompositionState state) {
        // Serialize with CompositionStage.ingest on the same row lock, then
        // re-check: the ingest we waited on may have composed it meanwhile.
        CompositionState locked = states
                .findWithLockingByCorrelationKey(state.getCorrelationKey()).orElse(null);
        if (locked == null || locked.getStatus() != CompositionStatus.PENDING) {
            return;
        }
        if (states.markExpired(state.getCorrelationKey(),
                CompositionStatus.EXPIRED, CompositionStatus.PENDING) == 1) {
            metrics.expired(state.getNamespaceKey());
            log.warn("composition {} expired with incomplete parts (required: {})",
                    state.getCorrelationKey(), state.getRequiredParts());
        }
    }
}
