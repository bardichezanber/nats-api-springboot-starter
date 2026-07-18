package com.example.ingest.worker.composition;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Composition housekeeping knobs. Expired/composed rows are kept for
 * {@code retention} for troubleshooting, then deleted by the sweeper.
 */
@ConfigurationProperties(prefix = "app.composition")
public record CompositionProperties(Duration retention) {

    public CompositionProperties {
        retention = retention == null ? Duration.ofDays(7) : retention;
    }
}
