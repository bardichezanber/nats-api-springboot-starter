package com.example.ingest.record;

import com.example.ingest.namespace.SourceKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The shared read model: every source and every event type lands here,
 * already normalized by its namespace policy.
 */
@Entity
@Table(name = "ingested_record")
public class IngestedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_key", nullable = false)
    private String namespaceKey;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected IngestedRecord() {
    }

    public static IngestedRecord of(String namespaceKey, SourceKey source, String eventType,
                                    String dedupKey, String payload, Instant occurredAt, Instant ingestedAt) {
        IngestedRecord record = new IngestedRecord();
        record.namespaceKey = namespaceKey;
        record.sourceKey = source.name();
        record.eventType = eventType;
        record.dedupKey = dedupKey;
        record.payload = payload;
        record.occurredAt = occurredAt;
        record.ingestedAt = ingestedAt;
        return record;
    }

    public Long getId() {
        return id;
    }

    public String getNamespaceKey() {
        return namespaceKey;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
