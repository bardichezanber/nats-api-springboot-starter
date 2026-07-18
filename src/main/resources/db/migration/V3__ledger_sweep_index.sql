-- The ledger sweeper deletes entries older than the retention cutoff.
CREATE INDEX idx_ledger_first_seen ON ingest_ledger (first_seen_at);
