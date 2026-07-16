package com.example.ingest.worker.nats;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * NATS connectivity for the worker role. Values live in
 * {@code application-worker.yml}; only worker-profile beans read them.
 */
@ConfigurationProperties(prefix = "app.nats")
public record NatsProperties(String url, SourceConfig sourceA, SourceConfig sourceB) {

    public record SourceConfig(String stream, String subject, String durable) {
    }
}
