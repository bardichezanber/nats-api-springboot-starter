package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * Example namespace: alpha payloads carry the record data as a flat
 * {@code data} object. Replace the parse logic with real business rules.
 */
@Component
public class AlphaNamespacePolicy implements NamespacePolicy {

    public static final String KEY = "alpha";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public JsonNode parse(CommonEnvelope envelope) {
        JsonNode data = envelope.body().path("data");
        if (!data.isObject()) {
            throw new IllegalArgumentException("alpha payload requires an object 'data' field");
        }
        return data;
    }
}
