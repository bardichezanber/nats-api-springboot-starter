package com.example.ingest.worker.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface IngestLedgerRepository extends JpaRepository<IngestLedgerEntry, String> {

    /** Retention: entries past the cutoff no longer guard against replays. */
    @Modifying
    @Query("delete from IngestLedgerEntry e where e.firstSeenAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
