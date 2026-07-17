package com.example.ingest.gateway;

import com.example.ingest.namespace.SourceKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Gateway-side metrics, same tag convention as IngestMetrics (source tag = SourceKey.key()). */
@Component
@Profile("gateway")
public class GatewayMetrics {

    private final MeterRegistry registry;
    private final AtomicLong scanLagSeconds = new AtomicLong();

    public GatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
        registry.gauge("gateway.ftp.scan.lag.seconds", scanLagSeconds);
    }

    public void published(SourceKey source) {
        publishCounter(source, "published").increment();
    }

    public void publishFailed(SourceKey source) {
        publishCounter(source, "failed").increment();
    }

    /** outcome: archived | error | failed */
    public void file(String outcome) {
        Counter.builder("gateway.ftp.files").tag("outcome", outcome).register(registry).increment();
    }

    /** Age of the oldest file still waiting in inbox/ (0 when empty). */
    public void scanLag(long seconds) {
        scanLagSeconds.set(seconds);
    }

    private Counter publishCounter(SourceKey source, String outcome) {
        return Counter.builder("gateway.publish")
                .tag("source", source.key())
                .tag("outcome", outcome)
                .register(registry);
    }
}
