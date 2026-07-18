package com.example.ingest.worker.ledger;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Ledger housekeeping knobs. Retention MUST stay comfortably longer than the
 * longest possible redelivery/replay window of any source stream: once an
 * entry is swept, a replay of that message passes the ledger check, hits the
 * {@code ingested_record} unique constraint instead, and is NAKed until max
 * deliveries.
 */
@ConfigurationProperties(prefix = "app.ledger")
public record IngestLedgerProperties(Duration retention) {

    public IngestLedgerProperties {
        retention = retention == null ? Duration.ofDays(30) : retention;
    }
}
