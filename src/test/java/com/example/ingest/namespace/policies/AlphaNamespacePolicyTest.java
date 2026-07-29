package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaNamespacePolicyTest {

    private final AlphaNamespacePolicy policy = new AlphaNamespacePolicy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsTheDataObjectAsPayload() throws JacksonException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","data":{"amount":42}}
                """);

        JsonNode parsed = policy.parse(envelope);

        assertThat(parsed.get("amount").asInt()).isEqualTo(42);
    }

    @Test
    void rejectsPayloadWithoutDataObject() throws JacksonException {
        CommonEnvelope envelope = envelope("""
                {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z"}
                """);

        assertThatThrownBy(() -> policy.parse(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data");
    }

    @Test
    void expiredMarkerMergesArrivedPartsAndListsTheMissingOnes() throws JacksonException {
        CommonEnvelope envelope = envelope("ready.composed.expired", """
                {"parts":{"x.ready":{"correlationId":"c-1","data":{"weight":10}}},"missing":["y.ready"]}
                """);

        JsonNode parsed = policy.parse(envelope);

        assertThat(parsed.get("partial").get("weight").asInt()).isEqualTo(10);
        assertThat(parsed.get("missing").get(0).asText()).isEqualTo("y.ready");
    }

    @Test
    void rejectsExpiredMarkerWithoutAMissingList() throws JacksonException {
        CommonEnvelope envelope = envelope("ready.composed.expired", """
                {"parts":{"x.ready":{"correlationId":"c-1","data":{"weight":10}}}}
                """);

        assertThatThrownBy(() -> policy.parse(envelope))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private CommonEnvelope envelope(String json) throws JacksonException {
        return envelope("orders.created", json);
    }

    private CommonEnvelope envelope(String eventType, String json) throws JacksonException {
        return new CommonEnvelope(SourceKey.SOURCE_A, eventType, "e-1",
                Instant.parse("2026-01-01T00:00:00Z"), objectMapper.readTree(json));
    }
}
