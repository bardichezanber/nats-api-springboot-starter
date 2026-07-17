package com.example.ingest.gateway;

import com.example.ingest.namespace.SourceKey;
import com.fasterxml.jackson.databind.JsonNode;

/** A validated event on its way from the gateway into JetStream. */
public record GatewayEvent(
        SourceKey source,
        String eventType,
        String namespaceKey,
        String dedupKey,
        JsonNode body) {
}
