package com.example.ingest.worker.composition.plans;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.composition.CompositionPlan;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaReadyCompositionPolicyTest {

    private final AlphaReadyCompositionPolicy policy = new AlphaReadyCompositionPolicy();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private CommonEnvelope envelope(String eventType, String json) throws JsonProcessingException {
        return envelope(SourceKey.SOURCE_A, eventType, json);
    }

    private CommonEnvelope envelope(SourceKey source, String eventType, String json)
            throws JsonProcessingException {
        return new CommonEnvelope(source, eventType, "e-1",
                Instant.parse("2026-01-01T00:00:00Z"), objectMapper.readTree(json));
    }

    @Test
    void claimsBothReadyHalvesForAlpha() throws JsonProcessingException {
        Optional<CompositionPlan> plan = policy.planFor("alpha",
                envelope("x.ready", "{\"correlationId\":\"c-1\",\"data\":{\"weight\":10}}"));

        assertThat(plan).hasValueSatisfying(p -> {
            assertThat(p.correlationKey()).isEqualTo("c-1");
            assertThat(p.partKey()).isEqualTo("x.ready");
            assertThat(p.requiredPartKeys()).isEqualTo(Set.of("x.ready", "y.ready"));
            assertThat(p.composedEventType()).isEqualTo("ready.composed");
        });
        assertThat(policy.planFor("alpha",
                envelope("y.ready", "{\"correlationId\":\"c-1\"}"))).isPresent();
    }

    @Test
    void ignoresOtherNamespacesAndEventTypes() throws JsonProcessingException {
        assertThat(policy.planFor("beta", envelope("x.ready", "{\"correlationId\":\"c-1\"}"))).isEmpty();
        assertThat(policy.planFor("beta", envelope("ready", "{}"))).isEmpty();
        assertThat(policy.planFor("alpha", envelope("orders.created", "{}"))).isEmpty();
    }

    @Test
    void ignoresAlphaReadyEventsFromOtherSources() throws JsonProcessingException {
        // The flow is scoped to route A: an x.ready reaching alpha via any
        // other source passes through untouched (no correlationId required).
        assertThat(policy.planFor("alpha",
                envelope(SourceKey.SOURCE_B, "x.ready", "{\"data\":{}}"))).isEmpty();
        assertThat(policy.planFor("alpha",
                envelope(SourceKey.SOURCE_HTTP, "y.ready", "{\"data\":{}}"))).isEmpty();
    }

    @Test
    void rejectsClaimedEventWithoutCorrelationId() throws JsonProcessingException {
        CommonEnvelope missing = envelope("x.ready", "{\"data\":{}}");

        assertThatThrownBy(() -> policy.planFor("alpha", missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlationId");
    }
}
