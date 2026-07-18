package com.example.ingest.worker.composition;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestPipeline;
import com.example.ingest.worker.IngestResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sits between the source resolvers and the pipeline. Events no
 * {@link CompositionPolicy} claims pass straight through (the normal path).
 * Claimed events are buffered until every required part arrived; the last
 * part's transaction builds the composed envelope and runs it through the
 * pipeline, so composition and ingest commit atomically.
 *
 * <p>Concurrency: the pessimistic lock on the state row serializes parts of
 * the same correlation. The state PK, the (correlation_key, part_key) unique
 * constraint and the guarded markComposed UPDATE are backstops — losing any
 * of those races surfaces as DUPLICATE (ack) or a nak-then-retry, never as a
 * lost or double-composed event.
 */
// Gateway boots without a database; every other context (worker, api, tests) loads this bean.
@Profile("!gateway")
@Service
public class CompositionStage {

    private static final Logger log = LoggerFactory.getLogger(CompositionStage.class);

    private final List<CompositionPolicy> policies;
    private final CompositionStateRepository states;
    private final CompositionPartRepository parts;
    private final IngestPipeline pipeline;
    private final CompositionMetrics metrics;
    private final ObjectMapper objectMapper;

    public CompositionStage(List<CompositionPolicy> policies,
                            CompositionStateRepository states,
                            CompositionPartRepository parts,
                            IngestPipeline pipeline,
                            CompositionMetrics metrics,
                            ObjectMapper objectMapper) {
        requireDisjointClaims(policies);
        this.policies = policies;
        this.states = states;
        this.parts = parts;
        this.pipeline = pipeline;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    /** Flows must be disjoint (see {@link CompositionPolicy}) — fail fast, not first-match-wins. */
    private static void requireDisjointClaims(List<CompositionPolicy> policies) {
        Map<String, String> claims = new HashMap<>();
        for (CompositionPolicy policy : policies) {
            for (String eventType : policy.claimedEventTypes()) {
                String claim = policy.namespace() + ":" + eventType;
                String other = claims.putIfAbsent(claim, policy.getClass().getName());
                if (other != null) {
                    throw new IllegalStateException("composition flows must be disjoint: ("
                            + claim + ") is claimed by both " + other
                            + " and " + policy.getClass().getName());
                }
            }
        }
    }

    @Transactional
    public IngestResult ingest(String namespaceKey, CommonEnvelope envelope) {
        Optional<CompositionPlan> claimed = planFor(namespaceKey, envelope);
        if (claimed.isEmpty()) {
            return pipeline.ingest(namespaceKey, envelope);
        }
        CompositionPlan plan = claimed.get();
        String key = namespaceKey + ":" + plan.correlationKey();

        Optional<CompositionState> state = states.findWithLockingByCorrelationKey(key);
        if (state.isEmpty()) {
            // PK is the backstop for a concurrent first part: the loser fails
            // at flush, is NAKed, and the redelivery sees the row.
            states.saveAndFlush(new CompositionState(key, namespaceKey, CompositionStatus.PENDING,
                    String.join(",", plan.requiredPartKeys()), plan.composedEventType(),
                    Instant.now().plus(plan.timeout()), Instant.now()));
        } else if (state.get().getStatus() != CompositionStatus.PENDING) {
            log.debug("dropping part {} for {} correlation {}", plan.partKey(),
                    state.get().getStatus(), key);
            return IngestResult.DUPLICATE;
        }

        if (parts.existsByCorrelationKeyAndPartKey(key, plan.partKey())) {
            return IngestResult.DUPLICATE;
        }
        parts.save(new CompositionPart(key, plan.partKey(), envelope.source().name(),
                envelope.eventType(), envelope.body().toString(), envelope.occurredAt(), Instant.now()));

        List<CompositionPart> stored = parts.findByCorrelationKey(key);
        Set<String> arrived = stored.stream().map(CompositionPart::getPartKey).collect(Collectors.toSet());
        if (!arrived.containsAll(plan.requiredPartKeys())) {
            return IngestResult.BUFFERED;
        }

        if (states.markComposed(key, Instant.now(), CompositionStatus.COMPOSED, CompositionStatus.PENDING) == 0) {
            return IngestResult.DUPLICATE;
        }
        IngestResult result = pipeline.ingest(namespaceKey, composedEnvelope(envelope, plan, key, stored));
        metrics.completed(namespaceKey);
        log.debug("composed {} from {} parts -> {}", key, stored.size(), result);
        return result;
    }

    private Optional<CompositionPlan> planFor(String namespaceKey, CommonEnvelope envelope) {
        for (CompositionPolicy policy : policies) {
            if (policy.namespace().equals(namespaceKey)
                    && policy.claimedEventTypes().contains(envelope.eventType())) {
                return policy.planFor(namespaceKey, envelope);
            }
        }
        return Optional.empty();
    }

    private CommonEnvelope composedEnvelope(CommonEnvelope trigger, CompositionPlan plan,
                                            String key, List<CompositionPart> stored) {
        ObjectNode body = JsonNodeFactory.instance.objectNode();
        for (CompositionPart part : stored) {
            body.set(part.getPartKey(), readPayload(part));
        }
        Instant occurredAt = stored.stream()
                .map(CompositionPart::getOccurredAt)
                .max(Comparator.naturalOrder())
                .orElse(trigger.occurredAt());
        // The trigger's source is a race outcome; the first-arrived part's is
        // stable, which also keeps the record UNIQUE(source, dedup) backstop
        // and the metric source tag deterministic.
        SourceKey source = SourceKey.valueOf(CompositionPart.firstArrived(stored).getSourceKey());
        return new CommonEnvelope(source, plan.composedEventType(), key, occurredAt, body);
    }

    private JsonNode readPayload(CompositionPart part) {
        try {
            return objectMapper.readTree(part.getPayload());
        } catch (IOException e) {
            throw new IllegalStateException("stored composition part " + part.getId() + " is not valid JSON", e);
        }
    }
}
