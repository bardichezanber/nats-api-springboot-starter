package com.example.ingest.worker.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Worker-only dedup ledger. The key is {@code sourceKey + ":" + dedupKey}.
 */
@Entity
@Table(name = "ingest_ledger")
public class IngestLedgerEntry {

    @Id
    @Column(name = "ledger_key")
    private String ledgerKey;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    protected IngestLedgerEntry() {
    }

    public IngestLedgerEntry(String ledgerKey, Instant firstSeenAt) {
        this.ledgerKey = ledgerKey;
        this.firstSeenAt = firstSeenAt;
    }

    public String getLedgerKey() {
        return ledgerKey;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }
}
