package com.example.ingest.namespace;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Source-agnostic view of an incoming message. The common fields
 * (dedup key, occurred-at) live at namespace-independent positions in the
 * payload, so they can be extracted before the namespace is resolved.
 */
public record CommonEnvelope(
        SourceKey source,
        String eventType,
        String dedupKey,
        Instant occurredAt,
        JsonNode body) {
}
