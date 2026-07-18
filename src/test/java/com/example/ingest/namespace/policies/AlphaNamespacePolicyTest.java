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

class AlphaNamespacePolicyTest {

    private final AlphaNamespacePolicy policy = new AlphaNamespacePolicy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsTheDataObjectAsPayload() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","data":{"amount":42}}
                """);

        JsonNode parsed = policy.parse(envelope);

        assertThat(parsed.get("amount").asInt()).isEqualTo(42);
    }

    @Test
    void rejectsPayloadWithoutDataObject() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z"}
                """);

        assertThatThrownBy(() -> policy.parse(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");
    }

    @Test
    void expiredMarkerMergesArrivedPartsAndListsTheMissingOnes() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("ready.composed.expired", """
                {"parts":{"x.ready":{"correlationId":"c-1","data":{"weight":10}}},"missing":["y.ready"]}
                """);

        JsonNode parsed = policy.parse(envelope);

        assertThat(parsed.get("partial").get("weight").asInt()).isEqualTo(10);
        assertThat(parsed.get("missing").get(0).asText()).isEqualTo("y.ready");
    }

    @Test
    void rejectsExpiredMarkerWithoutAMissingList() throws JsonProcessingException {
        CommonEnvelope envelope = envelope("ready.composed.expired", """
                {"parts":{"x.ready":{"correlationId":"c-1","data":{"weight":10}}}}
                """);

        assertThatThrownBy(() -> policy.parse(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private CommonEnvelope envelope(String json) throws JsonProcessingException {
        return envelope("orders.created", json);
    }

    private CommonEnvelope envelope(String eventType, String json) throws JsonProcessingException {
        return new CommonEnvelope(SourceKey.SOURCE_A, eventType, "e-1",
                Instant.parse("2026-01-01T00:00:00Z"), objectMapper.readTree(json));
    }
}
