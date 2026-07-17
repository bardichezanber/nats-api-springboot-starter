package com.example.ingest.worker.nats;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * NATS connectivity for the worker role. Values live in
 * {@code application-worker.yml}; only worker-profile beans read them.
 * The {@code sources} map is keyed by {@link com.example.ingest.namespace.SourceKey#key()}.
 */
@ConfigurationProperties(prefix = "app.nats")
public record NatsProperties(String url, Map<String, SourceConfig> sources) {

    public NatsProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }

    public record SourceConfig(String stream, String subject, String durable) {
    }
}
