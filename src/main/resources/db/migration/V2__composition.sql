-- Composition buffer: 1..3 events merge into one downstream event.
-- correlation_key is namespace-scoped: <namespace_key>:<plan correlation key>.
CREATE TABLE composition_state (
    correlation_key VARCHAR(256) PRIMARY KEY,
    namespace_key   VARCHAR(64)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    required_parts  VARCHAR(512) NOT NULL,
    deadline_at     DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    completed_at    DATETIME(6)  NULL
);
CREATE INDEX idx_composition_sweep ON composition_state (status, deadline_at);

CREATE TABLE composition_part (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    correlation_key VARCHAR(256) NOT NULL,
    part_key        VARCHAR(64)  NOT NULL,
    source_key      VARCHAR(32)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         TEXT         NOT NULL,
    occurred_at     DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uq_part UNIQUE (correlation_key, part_key)
);
