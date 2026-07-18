package com.example.ingest.worker.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One buffered event waiting for the rest of its composition. The
 * {@code (correlation_key, part_key)} unique constraint is the backstop
 * against duplicate parts under concurrent redelivery.
 */
@Entity
@Table(name = "composition_part")
public class CompositionPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correlation_key", nullable = false)
    private String correlationKey;

    @Column(name = "part_key", nullable = false)
    private String partKey;

    @Column(name = "source_key", nullable = false)
    private String sourceKey;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CompositionPart() {
    }

    public CompositionPart(String correlationKey, String partKey, String sourceKey,
                           String eventType, String payload, Instant occurredAt, Instant createdAt) {
        this.correlationKey = correlationKey;
        this.partKey = partKey;
        this.sourceKey = sourceKey;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public String getPartKey() {
        return partKey;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
