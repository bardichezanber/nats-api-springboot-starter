package com.example.ingest.worker.source;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** The namespace-independent fields every message carries, plus the raw body. */
public record CommonPayload(String dedupKey, Instant occurredAt, JsonNode body) {
}
