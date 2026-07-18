package com.example.ingest.worker.composition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per in-flight (or recently finished) composition. The key is
 * {@code namespaceKey + ":" + plan correlation key}; status transitions go
 * through the guarded repository UPDATEs, never through entity setters.
 */
@Entity
@Table(name = "composition_state")
public class CompositionState {

    @Id
    @Column(name = "correlation_key")
    private String correlationKey;

    @Column(name = "namespace_key", nullable = false)
    private String namespaceKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CompositionStatus status;

    @Column(name = "required_parts", nullable = false)
    private String requiredParts;

    // Nullable: rows created before V4 expire without a marker record.
    @Column(name = "composed_event_type")
    private String composedEventType;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CompositionState() {
    }

    public CompositionState(String correlationKey, String namespaceKey, CompositionStatus status,
                            String requiredParts, String composedEventType,
                            Instant deadlineAt, Instant createdAt) {
        this.correlationKey = correlationKey;
        this.namespaceKey = namespaceKey;
        this.status = status;
        this.requiredParts = requiredParts;
        this.composedEventType = composedEventType;
        this.deadlineAt = deadlineAt;
        this.createdAt = createdAt;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public String getNamespaceKey() {
        return namespaceKey;
    }

    public CompositionStatus getStatus() {
        return status;
    }

    public String getRequiredParts() {
        return requiredParts;
    }

    public String getComposedEventType() {
        return composedEventType;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
