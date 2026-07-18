package com.example.ingest.api;

import com.example.ingest.record.IngestedRecord;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * One keyset window of records plus the cursor for the next call. The cursor
 * fields are present whenever this window has records; the client pages by
 * passing them back until {@code records} comes back empty.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecordWindowResponse(
        List<RecordResponse> records,
        Instant nextOccurredBefore,
        Long nextIdBefore) {

    public static RecordWindowResponse from(List<IngestedRecord> window) {
        if (window.isEmpty()) {
            return new RecordWindowResponse(List.of(), null, null);
        }
        IngestedRecord last = window.getLast();
        return new RecordWindowResponse(
                window.stream().map(RecordResponse::from).toList(),
                last.getOccurredAt(),
                last.getId());
    }
}
