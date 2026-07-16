-- Shared read model: every source and event type lands here.
CREATE TABLE ingested_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    namespace_key VARCHAR(64)  NOT NULL,
    source_key    VARCHAR(32)  NOT NULL,
    event_type    VARCHAR(128) NOT NULL,
    dedup_key     VARCHAR(256) NOT NULL,
    payload       TEXT         NOT NULL,
    occurred_at   DATETIME(6)  NOT NULL,
    ingested_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uq_record_source_dedup UNIQUE (source_key, dedup_key)
);

-- Reads are always scoped to a single namespace (no cross-namespace queries).
CREATE INDEX idx_record_namespace_occurred ON ingested_record (namespace_key, occurred_at DESC);

-- Worker-only dedup ledger. ledger_key = source_key + ":" + dedup_key.
CREATE TABLE ingest_ledger (
    ledger_key    VARCHAR(320) PRIMARY KEY,
    first_seen_at DATETIME(6) NOT NULL
);
