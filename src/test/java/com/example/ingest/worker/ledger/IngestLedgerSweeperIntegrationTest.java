package com.example.ingest.worker.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IngestLedgerSweeperIntegrationTest {

    @Autowired
    private IngestLedgerSweeper sweeper;

    @Autowired
    private IngestLedgerRepository ledger;

    @BeforeEach
    void cleanDatabase() {
        ledger.deleteAll();
    }

    @Test
    void retentionRemovesOldEntriesButKeepsRecentOnes() {
        // Default retention is 30d: this entry is far past the cutoff ...
        ledger.save(new IngestLedgerEntry("SOURCE_A:old", Instant.parse("2020-01-01T00:00:00Z")));
        // ... and this one is fresh, still inside the dedup window.
        ledger.save(new IngestLedgerEntry("SOURCE_A:fresh", Instant.now()));

        sweeper.sweep();

        assertThat(ledger.findById("SOURCE_A:old")).isEmpty();
        assertThat(ledger.findById("SOURCE_A:fresh")).isPresent();
    }
}
