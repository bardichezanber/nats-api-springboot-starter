package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.record.IngestedRecordRepository;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.worker.ledger.IngestLedgerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.namespaces.enabled=alpha,beta")
class CompositionStageIntegrationTest {

    @Autowired
    private CompositionStage stage;

    @Autowired
    private CompositionStateRepository states;

    @Autowired
    private CompositionPartRepository parts;

    @Autowired
    private IngestedRecordRepository records;

    @Autowired
    private IngestLedgerRepository ledger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        parts.deleteAll();
        states.deleteAll();
        records.deleteAll();
        ledger.deleteAll();
    }

    private CommonEnvelope envelope(String eventType, String dedupKey, String json)
            throws JsonProcessingException {
        return new CommonEnvelope(SourceKey.SOURCE_A, eventType, dedupKey,
                Instant.parse("2026-01-01T00:00:00Z"), objectMapper.readTree(json));
    }

    private CommonEnvelope xReady(String correlationId) throws JsonProcessingException {
        return envelope("x.ready", "x-" + correlationId,
                "{\"correlationId\":\"" + correlationId + "\",\"data\":{\"weight\":10}}");
    }

    private CommonEnvelope yReady(String correlationId) throws JsonProcessingException {
        return envelope("y.ready", "y-" + correlationId,
                "{\"correlationId\":\"" + correlationId + "\",\"data\":{\"volume\":3}}");
    }

    @Test
    void alphaComposesWhenBothHalvesArrive() throws JsonProcessingException {
        assertThat(stage.ingest("alpha", xReady("c-1"))).isEqualTo(IngestResult.BUFFERED);
        assertThat(records.count()).isZero();

        assertThat(stage.ingest("alpha", yReady("c-1"))).isEqualTo(IngestResult.SAVED);

        assertThat(records.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getNamespaceKey()).isEqualTo("alpha");
            assertThat(saved.getEventType()).isEqualTo("ready.composed");
            assertThat(saved.getDedupKey()).isEqualTo("alpha:c-1");
            assertThat(saved.getPayload()).contains("\"weight\":10").contains("\"volume\":3");
        });
        assertThat(states.findById("alpha:c-1")).hasValueSatisfying(state ->
                assertThat(state.getStatus()).isEqualTo(CompositionStatus.COMPOSED));
    }

    @Test
    void duplicatePartIsDroppedWhilePending() throws JsonProcessingException {
        assertThat(stage.ingest("alpha", xReady("c-2"))).isEqualTo(IngestResult.BUFFERED);
        assertThat(stage.ingest("alpha", xReady("c-2"))).isEqualTo(IngestResult.DUPLICATE);
        assertThat(records.count()).isZero();
    }

    @Test
    void latePartAfterCompositionIsDropped() throws JsonProcessingException {
        stage.ingest("alpha", xReady("c-3"));
        stage.ingest("alpha", yReady("c-3"));

        assertThat(stage.ingest("alpha", xReady("c-3"))).isEqualTo(IngestResult.DUPLICATE);
        assertThat(records.count()).isEqualTo(1);
    }

    @Test
    void alphaEventFromRouteBIsNotPulledIntoTheComposition() throws JsonProcessingException {
        CommonEnvelope routeB = new CommonEnvelope(SourceKey.SOURCE_B, "x.ready", "b-x-1",
                Instant.parse("2026-01-01T00:00:00Z"),
                objectMapper.readTree("{\"data\":{\"weight\":10}}"));

        assertThat(stage.ingest("alpha", routeB)).isEqualTo(IngestResult.SAVED);

        assertThat(records.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getNamespaceKey()).isEqualTo("alpha");
            assertThat(saved.getEventType()).isEqualTo("x.ready");
            assertThat(saved.getSourceKey()).isEqualTo("SOURCE_B");
        });
        assertThat(states.count()).isZero();
        assertThat(parts.count()).isZero();
    }

    @Test
    void betaSingleReadyEventPassesStraightThrough() throws JsonProcessingException {
        CommonEnvelope ready = envelope("ready", "b-1",
                "{\"attributes\":[{\"name\":\"status\",\"value\":\"ok\"}]}");

        assertThat(stage.ingest("beta", ready)).isEqualTo(IngestResult.SAVED);

        assertThat(records.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getNamespaceKey()).isEqualTo("beta");
            assertThat(saved.getEventType()).isEqualTo("ready");
        });
        assertThat(states.count()).isZero();
        assertThat(parts.count()).isZero();
    }

    @Test
    void composedEventIsDedupedByTheLedgerLikeAnyOther() throws JsonProcessingException {
        stage.ingest("alpha", xReady("c-4"));
        stage.ingest("alpha", yReady("c-4"));
        assertThat(records.count()).isEqualTo(1);
        assertThat(ledger.existsById("SOURCE_A:alpha:c-4")).isTrue();
    }
}
