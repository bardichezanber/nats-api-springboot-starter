package com.example.ingest.worker.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestLedgerRepository extends JpaRepository<IngestLedgerEntry, String> {
}
