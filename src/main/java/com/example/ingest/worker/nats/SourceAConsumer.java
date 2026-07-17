package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestPipeline;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.worker.source.CommonPayload;
import com.example.ingest.worker.source.CommonPayloadReader;
import com.example.ingest.worker.source.SourceANamespaceResolver;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source A: subjects {@code src-a.events.<eventType>}; the namespace is
 * derived from the {@code X-Category} header plus the common {@code region}
 * body field.
 */
@Component
@Profile("worker")
public class SourceAConsumer implements SourceConsumer {

    public static final String SUBJECT_PREFIX = "src-a.events.";
    public static final String CATEGORY_HEADER = "X-Category";

    private static final Logger log = LoggerFactory.getLogger(SourceAConsumer.class);

    private final CommonPayloadReader payloadReader;
    private final SourceANamespaceResolver namespaceResolver;
    private final IngestPipeline pipeline;

    public SourceAConsumer(CommonPayloadReader payloadReader,
                           SourceANamespaceResolver namespaceResolver,
                           IngestPipeline pipeline) {
        this.payloadReader = payloadReader;
        this.namespaceResolver = namespaceResolver;
        this.pipeline = pipeline;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_A;
    }

    @Override
    public void onMessage(Message message) {
        try {
            IngestResult result = handle(message);
            log.debug("source A message on {} -> {}", message.getSubject(), result);
            message.ack();
        } catch (IllegalArgumentException e) {
            // Poison message: redelivery cannot fix it, terminate instead.
            log.warn("terminating malformed source A message on {}: {}", message.getSubject(), e.getMessage());
            message.term();
        } catch (Exception e) {
            log.error("failed to process source A message on {}", message.getSubject(), e);
            message.nak();
        }
    }

    IngestResult handle(Message message) {
        CommonPayload payload = payloadReader.read(message.getData());
        String category = message.getHeaders() == null ? null : message.getHeaders().getFirst(CATEGORY_HEADER);
        String namespaceKey = namespaceResolver.resolve(category, payload.body());
        CommonEnvelope envelope = new CommonEnvelope(
                SourceKey.SOURCE_A,
                eventType(message.getSubject()),
                payload.dedupKey(),
                payload.occurredAt(),
                payload.body());
        return pipeline.ingest(namespaceKey, envelope);
    }

    private static String eventType(String subject) {
        if (subject == null || !subject.startsWith(SUBJECT_PREFIX) || subject.length() == SUBJECT_PREFIX.length()) {
            throw new IllegalArgumentException("unexpected source A subject: " + subject);
        }
        return subject.substring(SUBJECT_PREFIX.length());
    }
}
