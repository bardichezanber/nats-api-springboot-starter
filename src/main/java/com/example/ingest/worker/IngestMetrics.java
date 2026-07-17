package com.example.ingest.worker;

import com.example.ingest.namespace.SourceKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Ingest metrics with the project-wide tag convention: {@code source}
 * ({@link SourceKey#key()}), {@code namespace}, {@code result}. Dashboards
 * and alerts slice along the same two axes as the architecture.
 */
@Component
public class IngestMetrics {

    private final MeterRegistry registry;

    public IngestMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** ingest.messages — one increment per handled message, whatever the outcome. */
    public void result(SourceKey source, String namespaceKey, IngestResult result) {
        Counter.builder("ingest.messages")
                .tag("source", source.key())
                .tag("namespace", namespaceKey)
                .tag("result", result.name())
                .register(registry)
                .increment();
    }

    /** ingest.handle — per-message processing latency, recorded even on failure. */
    public <T> T timeHandle(SourceKey source, Supplier<T> work) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return work.get();
        } finally {
            sample.stop(Timer.builder("ingest.handle")
                    .tag("source", source.key())
                    .register(registry));
        }
    }

    /** ingest.poison — messages terminated as malformed; a spike means the upstream contract broke. */
    public void poison(SourceKey source) {
        Counter.builder("ingest.poison")
                .tag("source", source.key())
                .register(registry)
                .increment();
    }
}
