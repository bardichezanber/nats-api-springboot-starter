package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.record.IngestedRecordRepository;
import com.example.ingest.worker.IngestResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.namespaces.enabled=alpha,beta")
class CompositionSweeperIntegrationTest {

    @Autowired
    private CompositionSweeper sweeper;

    @Autowired
    private CompositionStage stage;

    @Autowired
    private CompositionStateRepository states;

    @Autowired
    private CompositionPartRepository parts;

    @Autowired
    private IngestedRecordRepository records;

    @Autowired
    private MeterRegistry meterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        parts.deleteAll();
        states.deleteAll();
        records.deleteAll();
    }

    private static final Instant OLD = Instant.parse("2020-01-01T00:00:00Z");

    @Test
    void overduePendingStatesExpireAndLatePartsAreDropped() throws JsonProcessingException {
        // Deadline already passed, but the row is fresh — expired by this sweep,
        // not yet removed by retention.
        states.save(new CompositionState("alpha:late", "alpha", CompositionStatus.PENDING,
                "x.ready,y.ready", Instant.now().minusSeconds(1), Instant.now()));

        sweeper.sweep();

        assertThat(states.findById("alpha:late")).hasValueSatisfying(state ->
                assertThat(state.getStatus()).isEqualTo(CompositionStatus.EXPIRED));
        assertThat(meterRegistry.get("composition.expired").tag("namespace", "alpha")
                .counter().count()).isGreaterThanOrEqualTo(1.0);

        CommonEnvelope latePart = new CommonEnvelope(SourceKey.SOURCE_A, "x.ready", "x-late",
                Instant.now(), objectMapper.readTree("{\"correlationId\":\"late\",\"data\":{}}"));
        assertThat(stage.ingest("alpha", latePart)).isEqualTo(IngestResult.DUPLICATE);
        assertThat(records.count()).isZero();
    }

    @Test
    void retentionRemovesOldTerminalStatesWithTheirPartsButKeepsPending() {
        states.save(new CompositionState("alpha:done", "alpha", CompositionStatus.COMPOSED,
                "x.ready,y.ready", OLD, OLD));
        parts.save(new CompositionPart("alpha:done", "x.ready", "SOURCE_A", "x.ready", "{}", OLD, OLD));
        states.save(new CompositionState("alpha:fresh", "alpha", CompositionStatus.PENDING,
                "x.ready,y.ready", Instant.now().plusSeconds(900), Instant.now()));

        sweeper.sweep();

        assertThat(states.findById("alpha:done")).isEmpty();
        assertThat(parts.findByCorrelationKey("alpha:done")).isEmpty();
        assertThat(states.findById("alpha:fresh")).isPresent();
    }
}
