package com.example.ingest.worker;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.record.IngestedRecordRepository;
import com.example.ingest.worker.ledger.IngestLedgerRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.namespaces.enabled=alpha")
class IngestPipelineIntegrationTest {

    @Autowired
    private IngestPipeline pipeline;

    @Autowired
    private IngestedRecordRepository records;

    @Autowired
    private IngestLedgerRepository ledger;

    @Autowired
    private MeterRegistry meterRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        records.deleteAll();
        ledger.deleteAll();
    }

    @Test
    void savesRecordAndLedgerEntryForEnabledNamespace() throws JacksonException {
        IngestResult result = pipeline.ingest("alpha", envelope("e-1"));

        assertThat(result).isEqualTo(IngestResult.SAVED);
        assertThat(records.findAll()).singleElement().satisfies(saved -> {
            assertThat(saved.getNamespaceKey()).isEqualTo("alpha");
            assertThat(saved.getSourceKey()).isEqualTo("SOURCE_A");
            assertThat(saved.getEventType()).isEqualTo("orders.created");
            assertThat(saved.getDedupKey()).isEqualTo("e-1");
            assertThat(saved.getPayload()).isEqualTo("{\"amount\":42}");
        });
        assertThat(ledger.existsById("SOURCE_A:e-1")).isTrue();
    }

    @Test
    void secondDeliveryOfTheSameMessageIsDuplicate() throws JacksonException {
        assertThat(pipeline.ingest("alpha", envelope("e-1"))).isEqualTo(IngestResult.SAVED);
        assertThat(pipeline.ingest("alpha", envelope("e-1"))).isEqualTo(IngestResult.DUPLICATE);

        assertThat(records.count()).isEqualTo(1);
    }

    @Test
    void knownButDisabledNamespaceIsDroppedWithoutWriting() throws JacksonException {
        IngestResult result = pipeline.ingest("beta", envelope("e-1"));

        assertThat(result).isEqualTo(IngestResult.NAMESPACE_DISABLED);
        assertThat(records.count()).isZero();
        assertThat(ledger.count()).isZero();
    }

    @Test
    void unknownNamespaceIsDroppedWithoutWriting() throws JacksonException {
        IngestResult result = pipeline.ingest("nope", envelope("e-1"));

        assertThat(result).isEqualTo(IngestResult.UNKNOWN_NAMESPACE);
        assertThat(records.count()).isZero();
        assertThat(ledger.count()).isZero();
    }

    @Test
    void countsEachResultBySourceAndNamespace() throws JacksonException {
        pipeline.ingest("alpha", envelope("metrics-1"));

        assertThat(meterRegistry.get("ingest.messages")
                .tags("source", "source-a", "namespace", "alpha", "result", "SAVED")
                .counter().count()).isGreaterThanOrEqualTo(1.0);
    }

    private CommonEnvelope envelope(String dedupKey) throws JacksonException {
        return new CommonEnvelope(SourceKey.SOURCE_A, "orders.created", dedupKey,
                Instant.parse("2026-01-01T00:00:00Z"),
                objectMapper.readTree("{\"eventId\":\"" + dedupKey + "\",\"data\":{\"amount\":42}}"));
    }
}
