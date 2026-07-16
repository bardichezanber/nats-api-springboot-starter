package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BetaNamespacePolicyTest {

    private final BetaNamespacePolicy policy = new BetaNamespacePolicy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesAttributePairsIntoAnObject() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z",
                 "attributes":[{"name":"amount","value":42},{"name":"currency","value":"usd"}]}
                """);

        JsonNode parsed = policy.parse(envelope);

        assertThat(parsed.get("amount").asInt()).isEqualTo(42);
        assertThat(parsed.get("currency").asText()).isEqualTo("usd");
    }

    @Test
    void rejectsPayloadWithoutAttributesArray() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","data":{}}
                """);

        assertThatThrownBy(() -> policy.parse(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributes");
    }

    private CommonEnvelope envelope(String json) throws JsonProcessingException {
        return new CommonEnvelope(SourceKey.SOURCE_B, "orders.created", "e-1",
                Instant.parse("2026-01-01T00:00:00Z"), objectMapper.readTree(json));
    }
}
