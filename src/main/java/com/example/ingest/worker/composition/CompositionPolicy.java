package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;

import java.util.Optional;

/**
 * One class per composition flow, in {@code worker/composition/plans/}.
 * Flows must be disjoint: at most one policy may claim any
 * (namespace, envelope) pair. Empty means passthrough — the event goes
 * straight to the pipeline, which is the normal single-event path.
 *
 * <p>Throw {@link IllegalArgumentException} when an event this flow claims
 * is malformed (e.g. missing its correlation field) — the consumer
 * terminates it as poison.
 */
public interface CompositionPolicy {

    Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope);
}
