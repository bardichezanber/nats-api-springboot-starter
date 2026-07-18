package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;

import java.util.Optional;
import java.util.Set;

/**
 * One class per composition flow, in {@code worker/composition/plans/}.
 * A flow declares its claim — one namespace plus the event types it buffers —
 * and {@code CompositionStage} only consults {@link #planFor} for events
 * matching that claim. Flows must be disjoint: the stage fails at startup if
 * two policies claim the same (namespace, event type) pair.
 *
 * <p>{@link #planFor} may still return empty for a claimed event (e.g. wrong
 * source) — the event then passes straight to the pipeline, the normal
 * single-event path.
 *
 * <p>Throw {@link IllegalArgumentException} when an event this flow claims
 * is malformed (e.g. missing its correlation field) — the consumer
 * terminates it as poison.
 */
public interface CompositionPolicy {

    /** The single namespace this flow belongs to. */
    String namespace();

    /** Event types this flow buffers, within {@link #namespace()}. */
    Set<String> claimedEventTypes();

    Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope);
}
