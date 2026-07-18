package com.example.ingest.worker.composition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Housekeeping for the composition buffer (decision D3): overdue PENDING
 * correlations become EXPIRED — discarded with a metric and a warn log, no
 * partial ingest — and terminal rows past retention are deleted together
 * with their parts.
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

    public CompositionSweeper(CompositionStateRepository states,
                              CompositionPartRepository parts,
                              CompositionMetrics metrics,
                              CompositionProperties properties) {
        this.states = states;
        this.parts = parts;
        this.metrics = metrics;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.composition.sweep-interval:60s}")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();

        List<CompositionState> overdue =
                states.findByStatusAndDeadlineAtBefore(CompositionStatus.PENDING, now);
        for (CompositionState state : overdue) {
            if (states.markExpired(state.getCorrelationKey(),
                    CompositionStatus.EXPIRED, CompositionStatus.PENDING) == 1) {
                metrics.expired(state.getNamespaceKey());
                log.warn("composition {} expired with incomplete parts (required: {})",
                        state.getCorrelationKey(), state.getRequiredParts());
            }
        }

        Instant cutoff = now.minus(properties.retention());
        int deletedStates = states.deleteTerminalOlderThan(CompositionStatus.PENDING, cutoff);
        int deletedParts = parts.deleteOrphansOlderThan(cutoff);
        if (deletedStates > 0 || deletedParts > 0) {
            log.info("composition retention removed {} states and {} parts", deletedStates, deletedParts);
        }
    }
}
