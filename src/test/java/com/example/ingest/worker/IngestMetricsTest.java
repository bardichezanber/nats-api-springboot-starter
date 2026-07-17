package com.example.ingest.worker;

import com.example.ingest.namespace.SourceKey;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IngestMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IngestMetrics metrics = new IngestMetrics(registry);

    @Test
    void countsResultsWithSourceNamespaceAndResultTags() {
        metrics.result(SourceKey.SOURCE_A, "alpha", IngestResult.SAVED);
        metrics.result(SourceKey.SOURCE_A, "alpha", IngestResult.SAVED);
        metrics.result(SourceKey.SOURCE_A, "alpha", IngestResult.DUPLICATE);

        assertThat(registry.get("ingest.messages")
                .tags("source", "source-a", "namespace", "alpha", "result", "SAVED")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("ingest.messages")
                .tags("source", "source-a", "namespace", "alpha", "result", "DUPLICATE")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void timesHandleAndReturnsTheResult() {
        String out = metrics.timeHandle(SourceKey.SOURCE_B, () -> "ok");

        assertThat(out).isEqualTo("ok");
        assertThat(registry.get("ingest.handle").tag("source", "source-b").timer().count()).isEqualTo(1);
    }

    @Test
    void timesHandleEvenWhenItThrows() {
        assertThatThrownBy(() -> metrics.timeHandle(SourceKey.SOURCE_B, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("ingest.handle").tag("source", "source-b").timer().count()).isEqualTo(1);
    }

    @Test
    void countsPoisonPerSource() {
        metrics.poison(SourceKey.SOURCE_A);

        assertThat(registry.get("ingest.poison").tag("source", "source-a").counter().count()).isEqualTo(1.0);
    }
}
