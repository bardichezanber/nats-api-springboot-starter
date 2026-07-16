package com.example.ingest.api;

import com.example.ingest.record.IngestedRecord;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

public record RecordResponse(
        Long id,
        String namespaceKey,
        String sourceKey,
        String eventType,
        String dedupKey,
        @JsonRawValue String payload,
        Instant occurredAt,
        Instant ingestedAt) {

    public static RecordResponse from(IngestedRecord record) {
        return new RecordResponse(
                record.getId(),
                record.getNamespaceKey(),
                record.getSourceKey(),
                record.getEventType(),
                record.getDedupKey(),
                record.getPayload(),
                record.getOccurredAt(),
                record.getIngestedAt());
    }
}
