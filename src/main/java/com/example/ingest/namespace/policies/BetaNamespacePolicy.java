package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

/**
 * Example namespace: beta payloads carry the record data as an
 * {@code attributes} array of name/value pairs, normalized here into an
 * object. Structurally different from alpha on purpose — namespaces may
 * change <em>how</em> the common payload is parsed, not just parameters.
 */
@Component
public class BetaNamespacePolicy implements NamespacePolicy {

    public static final String KEY = "beta";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public JsonNode parse(CommonEnvelope envelope) {
        JsonNode attributes = envelope.body().path("attributes");
        if (!attributes.isArray()) {
            throw new IllegalArgumentException("beta payload requires an 'attributes' array");
        }
        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        for (JsonNode attribute : attributes) {
            JsonNode name = attribute.path("name");
            if (!name.isTextual()) {
                throw new IllegalArgumentException("beta attribute entries require a textual 'name'");
            }
            normalized.set(name.asText(), attribute.path("value"));
        }
        return normalized;
    }
}
