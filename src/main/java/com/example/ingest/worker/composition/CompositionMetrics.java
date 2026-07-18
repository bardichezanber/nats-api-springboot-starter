package com.example.ingest.worker.composition;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Gateway boots without a database; every other context (worker, api, tests) loads this bean.
@Profile("!gateway")
@Component
public class CompositionMetrics {

    private final MeterRegistry registry;

    public CompositionMetrics(MeterRegistry registry, CompositionStateRepository states) {
        this.registry = registry;
        registry.gauge("composition.active", states,
                repository -> repository.countByStatus(CompositionStatus.PENDING));
    }

    public void completed(String namespaceKey) {
        counter("composition.completed", namespaceKey).increment();
    }

    public void expired(String namespaceKey) {
        counter("composition.expired", namespaceKey).increment();
    }

    private Counter counter(String name, String namespaceKey) {
        return Counter.builder(name).tag("namespace", namespaceKey).register(registry);
    }
}
