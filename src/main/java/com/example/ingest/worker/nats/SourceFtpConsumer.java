package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.worker.source.SourceFtpNamespaceResolver;
import io.nats.client.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source FTP: subjects {@code src-ftp.events.<eventType>}, published by the
 * gateway's file poller; the namespace is declared in the
 * {@code X-Namespace} header.
 */
@Component
@Profile("worker")
public class SourceFtpConsumer implements SourceConsumer {

    public static final String SUBJECT_PREFIX = "src-ftp.events.";
    public static final String NAMESPACE_HEADER = "X-Namespace";

    private static final Logger log = LoggerFactory.getLogger(SourceFtpConsumer.class);

    private final CommonPayloadReader payloadReader;
    private final SourceFtpNamespaceResolver namespaceResolver;
    private final CompositionStage compositionStage;
    private final IngestMetrics metrics;

    public SourceFtpConsumer(CommonPayloadReader payloadReader,
                             SourceFtpNamespaceResolver namespaceResolver,
                             CompositionStage compositionStage,
                             IngestMetrics metrics) {
        this.payloadReader = payloadReader;
        this.namespaceResolver = namespaceResolver;
        this.compositionStage = compositionStage;
        this.metrics = metrics;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_FTP;
    }

    @Override
    public void onMessage(Message message) {
        try {
            IngestResult result = metrics.timeHandle(source(), () -> handle(message));
            log.debug("source FTP message on {} -> {}", message.getSubject(), result);
            message.ack();
        } catch (IllegalArgumentException e) {
            // Poison message: redelivery cannot fix it, terminate instead.
            metrics.poison(source());
            log.warn("terminating malformed source FTP message on {}: {}", message.getSubject(), e.getMessage());
            message.term();
        } catch (Exception e) {
            log.error("failed to process source FTP message on {}", message.getSubject(), e);
            message.nak();
        }
    }

    IngestResult handle(Message message) {
        CommonPayload payload = payloadReader.read(message.getData());
        String header = message.getHeaders() == null ? null : message.getHeaders().getFirst(NAMESPACE_HEADER);
        String namespaceKey = namespaceResolver.resolve(header);
        CommonEnvelope envelope = new CommonEnvelope(
                source(),
                eventType(message.getSubject()),
                payload.dedupKey(),
                payload.occurredAt(),
                payload.body());
        return compositionStage.ingest(namespaceKey, envelope);
    }

    private static String eventType(String subject) {
        if (subject == null || !subject.startsWith(SUBJECT_PREFIX) || subject.length() == SUBJECT_PREFIX.length()) {
            throw new IllegalArgumentException("unexpected source FTP subject: " + subject);
        }
        return subject.substring(SUBJECT_PREFIX.length());
    }
}
