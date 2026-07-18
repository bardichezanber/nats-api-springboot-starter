package com.example.ingest.worker.composition;

import java.time.Duration;
import java.util.Set;

/**
 * How one event participates in a composition: which correlation it belongs
 * to, which part it is, which parts must all arrive, how long to wait, and
 * the event type of the composed result.
 */
public record CompositionPlan(
        String correlationKey,
        String partKey,
        Set<String> requiredPartKeys,
        Duration timeout,
        String composedEventType) {
}
