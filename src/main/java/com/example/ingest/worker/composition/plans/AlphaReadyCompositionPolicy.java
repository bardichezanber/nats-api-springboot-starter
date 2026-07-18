package com.example.ingest.worker.composition.plans;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.worker.composition.CompositionPlan;
import com.example.ingest.worker.composition.CompositionPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Example flow: alpha's record is only meaningful once BOTH halves arrived —
 * {@code x.ready} and {@code y.ready} carrying the same {@code correlationId}
 * body field. The composed event ({@code ready.composed}) is what alpha's
 * namespace policy parses. Beta's single {@code ready} event is deliberately
 * claimed by no policy: it passes straight through.
 */
@Component
public class AlphaReadyCompositionPolicy implements CompositionPolicy {

    public static final String COMPOSED_EVENT_TYPE = "ready.composed";
    public static final Set<String> REQUIRED_PARTS = Set.of("x.ready", "y.ready");

    private static final Duration TIMEOUT = Duration.ofMinutes(15);

    @Override
    public Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope) {
        if (!"alpha".equals(namespaceKey) || !REQUIRED_PARTS.contains(envelope.eventType())) {
            return Optional.empty();
        }
        JsonNode correlationId = envelope.body().path("correlationId");
        if (!correlationId.isTextual() || correlationId.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "alpha " + envelope.eventType() + " event requires a textual 'correlationId'");
        }
        return Optional.of(new CompositionPlan(
                correlationId.asText(),
                envelope.eventType(),
                REQUIRED_PARTS,
                TIMEOUT,
                COMPOSED_EVENT_TYPE));
    }
}
