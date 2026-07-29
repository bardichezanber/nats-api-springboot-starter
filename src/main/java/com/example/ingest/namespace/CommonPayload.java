package com.example.ingest.namespace;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/** The namespace-independent fields every message carries, plus the raw body. */
public record CommonPayload(String dedupKey, Instant occurredAt, JsonNode body) {
}
