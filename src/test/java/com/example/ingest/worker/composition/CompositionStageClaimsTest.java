package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestPipeline;
import com.example.ingest.worker.IngestResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompositionStageClaimsTest {

    private record FakePolicy(String namespace, Set<String> claimedEventTypes)
            implements CompositionPolicy {

        @Override
        public Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope) {
            return Optional.empty();
        }
    }

    private final IngestPipeline pipeline = mock(IngestPipeline.class);

    private CompositionStage stage(CompositionPolicy... policies) {
        return new CompositionStage(List.of(policies),
                mock(CompositionStateRepository.class), mock(CompositionPartRepository.class),
                pipeline, mock(CompositionMetrics.class), new ObjectMapper());
    }

    @Test
    void overlappingClaimsFailStartup() {
        FakePolicy flowOne = new FakePolicy("alpha", Set.of("x.ready", "y.ready"));
        FakePolicy flowTwo = new FakePolicy("alpha", Set.of("y.ready", "z.ready"));

        assertThatIllegalStateException()
                .isThrownBy(() -> stage(flowOne, flowTwo))
                .withMessageContaining("alpha")
                .withMessageContaining("y.ready");
    }

    @Test
    void disjointClaimsAreAccepted() {
        FakePolicy alphaFlow = new FakePolicy("alpha", Set.of("x.ready", "y.ready"));
        FakePolicy betaFlow = new FakePolicy("beta", Set.of("x.ready"));

        assertThatCode(() -> stage(alphaFlow, betaFlow)).doesNotThrowAnyException();
    }

    @Test
    void eventTypeClaimedByAnotherNamespacePassesThrough() {
        // beta claims x.ready; an alpha x.ready event must not be routed to it.
        FakePolicy betaFlow = new FakePolicy("beta", Set.of("x.ready"));
        CommonEnvelope envelope = new CommonEnvelope(SourceKey.SOURCE_A, "x.ready", "d-1",
                Instant.now(), new ObjectMapper().createObjectNode());
        when(pipeline.ingest("alpha", envelope)).thenReturn(IngestResult.SAVED);

        assertThat(stage(betaFlow).ingest("alpha", envelope)).isEqualTo(IngestResult.SAVED);
    }
}
