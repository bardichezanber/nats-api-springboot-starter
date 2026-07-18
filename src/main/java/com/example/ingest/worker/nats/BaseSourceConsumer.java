package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.worker.composition.CompositionStage;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared skeleton for all source consumers. Owns the message-handling
 * semantics of hard rule 7 — handled fine → ack, {@code IllegalArgumentException}
 * (malformed, poison) → term with a poison counter, anything else → nak — and
 * the common pipeline: read the common payload, parse the event type from the
 * subject ({@link com.example.ingest.namespace.SourceKey#subjectPrefix()}),
 * build the envelope, hand it to the {@link CompositionStage}.
 *
 * <p>A concrete source implements only {@link #source()} and
 * {@link #resolveNamespace(Message, CommonPayload)}. {@code onMessage} is
 * final on purpose: the ack/term/nak flow must stay identical across sources.
 */
public abstract class BaseSourceConsumer implements SourceConsumer {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CommonPayloadReader payloadReader;
    private final CompositionStage compositionStage;
    private final IngestMetrics metrics;

    protected BaseSourceConsumer(CommonPayloadReader payloadReader,
                                 CompositionStage compositionStage,
                                 IngestMetrics metrics) {
        this.payloadReader = payloadReader;
        this.compositionStage = compositionStage;
        this.metrics = metrics;
    }

    /**
     * Decide the namespace from the message and its common payload. Throw
     * {@link IllegalArgumentException} if the message does not follow the
     * source's contract — that terminates it as poison.
     */
    protected abstract String resolveNamespace(Message message, CommonPayload payload);

    @Override
    public final void onMessage(Message message) {
        try {
            IngestResult result = metrics.timeHandle(source(), () -> handle(message));
            log.debug("source {} message on {} -> {}", source().key(), message.getSubject(), result);
            message.ack();
        } catch (IllegalArgumentException e) {
            // Poison message: redelivery cannot fix it, terminate instead.
            metrics.poison(source());
            log.warn("terminating malformed source {} message on {}: {}",
                    source().key(), message.getSubject(), e.getMessage());
            message.term();
        } catch (Exception e) {
            log.error("failed to process source {} message on {}", source().key(), message.getSubject(), e);
            message.nak();
        }
    }

    final IngestResult handle(Message message) {
        CommonPayload payload = payloadReader.read(message.getData());
        String namespaceKey = resolveNamespace(message, payload);
        CommonEnvelope envelope = new CommonEnvelope(
                source(),
                eventType(message.getSubject()),
                payload.dedupKey(),
                payload.occurredAt(),
                payload.body());
        return compositionStage.ingest(namespaceKey, envelope);
    }

    /** Null-safe header lookup for {@link #resolveNamespace(Message, CommonPayload)}. */
    protected static String header(Message message, String name) {
        return message.getHeaders() == null ? null : message.getHeaders().getFirst(name);
    }

    private String eventType(String subject) {
        String prefix = source().subjectPrefix();
        if (subject == null || !subject.startsWith(prefix) || subject.length() == prefix.length()) {
            throw new IllegalArgumentException(
                    "unexpected source " + source().key() + " subject: " + subject);
        }
        return subject.substring(prefix.length());
    }
}
