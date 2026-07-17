package com.example.ingest.worker.nats;

import com.example.ingest.namespace.SourceKey;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The enabled sources of this worker, resolved to their consumer bean and
 * NATS config. Fails fast at startup if config enables a source that has no
 * {@link SourceConsumer} bean or no {@code app.nats.sources} entry — same
 * philosophy as {@link com.example.ingest.namespace.NamespaceRegistry}.
 */
@Component
@Profile("worker")
public class SourceRegistry {

    public record Subscription(SourceKey source, NatsProperties.SourceConfig config, SourceConsumer consumer) {
    }

    private final List<Subscription> subscriptions;

    public SourceRegistry(SourceProperties sourceProperties,
                          NatsProperties natsProperties,
                          List<SourceConsumer> consumers) {
        Map<SourceKey, SourceConsumer> bySource = consumers.stream()
                .collect(Collectors.toUnmodifiableMap(SourceConsumer::source, Function.identity()));

        List<Subscription> resolved = new ArrayList<>();
        for (String key : sourceProperties.enabled()) {
            SourceKey source;
            try {
                source = SourceKey.fromKey(key);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Enabled source has no SourceKey constant: " + key);
            }
            SourceConsumer consumer = bySource.get(source);
            if (consumer == null) {
                throw new IllegalStateException("Enabled source has no consumer bean: " + key);
            }
            NatsProperties.SourceConfig config = natsProperties.sources().get(key);
            if (config == null) {
                throw new IllegalStateException("Enabled source has no app.nats.sources entry: " + key);
            }
            resolved.add(new Subscription(source, config, consumer));
        }
        this.subscriptions = List.copyOf(resolved);
    }

    /** Enabled subscriptions, in configuration order. */
    public List<Subscription> subscriptions() {
        return subscriptions;
    }
}
