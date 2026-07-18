package com.example.ingest.worker.composition;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling is enabled for the worker role only, so the sweeper's
 * {@code @Scheduled} method never fires in api or test contexts (tests call
 * {@code sweep()} directly).
 */
@Configuration
@Profile("worker")
@EnableScheduling
public class CompositionConfig {
}
