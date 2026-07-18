package com.example.ingest.worker.ledger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Keeps the dedup ledger bounded: entries older than
 * {@code app.ledger.retention} (default 30d) are deleted. See
 * {@link IngestLedgerProperties} for why retention must exceed every
 * stream's replay window.
 */
// Gateway boots without a database; every other context (worker, api, tests) loads this bean.
@Profile("!gateway")
@Component
public class IngestLedgerSweeper {

    private static final Logger log = LoggerFactory.getLogger(IngestLedgerSweeper.class);

    private final IngestLedgerRepository ledger;
    private final IngestLedgerProperties properties;

    public IngestLedgerSweeper(IngestLedgerRepository ledger, IngestLedgerProperties properties) {
        this.ledger = ledger;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.ledger.sweep-interval:1h}")
    @Transactional
    public void sweep() {
        int deleted = ledger.deleteOlderThan(Instant.now().minus(properties.retention()));
        if (deleted > 0) {
            log.info("ledger retention removed {} entries", deleted);
        }
    }
}
