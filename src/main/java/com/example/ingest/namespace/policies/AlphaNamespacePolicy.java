package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Example namespace: alpha payloads carry the record data as a flat
 * {@code data} object. Replace the parse logic with real business rules.
 *
 * <p>Alpha also owns the shape of its composed event: {@code ready.composed}
 * arrives from the worker's composition stage with body
 * {@code {"x.ready": <x body>, "y.ready": <y body>}} and is normalized by
 * merging both halves' {@code data} objects. (The event type is a plain
 * string on purpose — namespace code must not depend on worker code.)
 */
@Component
public class AlphaNamespacePolicy implements NamespacePolicy {

    public static final String KEY = "alpha";
    public static final String READY_COMPOSED_EVENT_TYPE = "ready.composed";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public JsonNode parse(CommonEnvelope envelope) {
        if (READY_COMPOSED_EVENT_TYPE.equals(envelope.eventType())) {
            return parseReadyComposed(envelope.body());
        }
        JsonNode data = envelope.body().path("data");
        if (!data.isObject()) {
            throw new IllegalArgumentException("alpha payload requires an object 'data' field");
        }
        return data;
    }

    private static JsonNode parseReadyComposed(JsonNode body) {
        JsonNode x = body.path("x.ready").path("data");
        JsonNode y = body.path("y.ready").path("data");
        if (!x.isObject() || !y.isObject()) {
            throw new IllegalArgumentException(
                    "alpha ready.composed requires object 'data' fields in both x.ready and y.ready parts");
        }
        ObjectNode merged = JsonNodeFactory.instance.objectNode();
        merged.setAll((ObjectNode) x);
        merged.setAll((ObjectNode) y);
        return merged;
    }
}
