package com.example.ingest.worker.nats;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Deployment config decides which sources this worker consumes
 * ({@code APP_SOURCES_ENABLED}, comma-separated {@code SourceKey.key()} values).
 * Switch semantics, like namespaces: a source not listed is not subscribed,
 * so one image can be deployed per route and scaled independently.
 */
@ConfigurationProperties(prefix = "app.sources")
public record SourceProperties(List<String> enabled) {

    public SourceProperties {
        enabled = enabled == null ? List.of() : List.copyOf(enabled);
    }
}
