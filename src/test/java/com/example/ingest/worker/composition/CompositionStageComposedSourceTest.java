package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestPipeline;
import com.example.ingest.worker.IngestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The composed envelope's source must not depend on which part happened to
 * arrive last (that is a race outcome): it is the source of the part that
 * opened the correlation.
 */
class CompositionStageComposedSourceTest {

    private static final Instant EARLIER = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-01-01T00:05:00Z");

    private record TwoPartFlow() implements CompositionPolicy {

        @Override
        public String namespace() {
            return "alpha";
        }

        @Override
        public Set<String> claimedEventTypes() {
            return Set.of("x.ready", "y.ready");
        }

        @Override
        public Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope) {
            return Optional.of(new CompositionPlan("c-1", envelope.eventType(),
                    Set.of("x.ready", "y.ready"), Duration.ofMinutes(15), "combo.composed"));
        }
    }

    @Test
    void composedEnvelopeCarriesTheFirstArrivedPartsSource() {
        CompositionStateRepository states = mock(CompositionStateRepository.class);
        CompositionPartRepository parts = mock(CompositionPartRepository.class);
        IngestPipeline pipeline = mock(IngestPipeline.class);

        // The x part arrived first from SOURCE_A; the trigger is the later
        // y part from SOURCE_B.
        CompositionState pending = new CompositionState("alpha:c-1", "alpha",
                CompositionStatus.PENDING, "x.ready,y.ready", "combo.composed",
                LATER.plusSeconds(900), EARLIER);
        when(states.findWithLockingByCorrelationKey("alpha:c-1")).thenReturn(Optional.of(pending));
        when(parts.existsByCorrelationKeyAndPartKey("alpha:c-1", "y.ready")).thenReturn(false);
        when(parts.findByCorrelationKey("alpha:c-1")).thenReturn(List.of(
                new CompositionPart("alpha:c-1", "y.ready", "SOURCE_B", "y.ready", "{}", LATER, LATER),
                new CompositionPart("alpha:c-1", "x.ready", "SOURCE_A", "x.ready", "{}", EARLIER, EARLIER)));
        when(states.markComposed(eq("alpha:c-1"), any(), any(), any())).thenReturn(1);
        when(pipeline.ingest(anyString(), any())).thenReturn(IngestResult.SAVED);

        CompositionStage stage = new CompositionStage(List.of(new TwoPartFlow()),
                states, parts, pipeline, mock(CompositionMetrics.class), new ObjectMapper());
        CommonEnvelope trigger = new CommonEnvelope(SourceKey.SOURCE_B, "y.ready", "y-1",
                LATER, new ObjectMapper().createObjectNode());

        assertThat(stage.ingest("alpha", trigger)).isEqualTo(IngestResult.SAVED);

        ArgumentCaptor<CommonEnvelope> composed = ArgumentCaptor.forClass(CommonEnvelope.class);
        org.mockito.Mockito.verify(pipeline).ingest(eq("alpha"), composed.capture());
        assertThat(composed.getValue().source()).isEqualTo(SourceKey.SOURCE_A);
        assertThat(composed.getValue().eventType()).isEqualTo("combo.composed");
    }
}
