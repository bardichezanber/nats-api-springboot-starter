package com.example.ingest.worker.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Extracts the common fields shared by all sources and namespaces:
 * {@code eventId} (the dedup key) and {@code occurredAt}. Their position in
 * the payload is guaranteed namespace-independent, so this runs before the
 * namespace is even resolved.
 */
@Component
public class CommonPayloadReader {

    private final ObjectMapper objectMapper;

    public CommonPayloadReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CommonPayload read(byte[] data) {
        JsonNode body;
        try {
            body = objectMapper.readTree(data);
        } catch (IOException e) {
            throw new IllegalArgumentException("message body is not valid JSON", e);
        }

        JsonNode eventId = body.path("eventId");
        if (!eventId.isTextual() || eventId.asText().isBlank()) {
            throw new IllegalArgumentException("missing common field 'eventId'");
        }

        JsonNode occurredAt = body.path("occurredAt");
        if (!occurredAt.isTextual()) {
            throw new IllegalArgumentException("missing common field 'occurredAt'");
        }
        Instant occurredAtInstant;
        try {
            occurredAtInstant = Instant.parse(occurredAt.asText());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'occurredAt' is not an ISO-8601 instant", e);
        }

        return new CommonPayload(eventId.asText(), occurredAtInstant, body);
    }
}
