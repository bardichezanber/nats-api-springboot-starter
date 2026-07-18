package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.source.SourceANamespaceResolver;
import io.nats.client.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source A: subjects {@code src-a.events.<eventType>}; the namespace is
 * derived from the {@code X-Category} header plus the common {@code region}
 * body field.
 */
@Component
@Profile("worker")
public class SourceAConsumer extends BaseSourceConsumer {

    public static final String CATEGORY_HEADER = "X-Category";

    private final SourceANamespaceResolver namespaceResolver;

    public SourceAConsumer(CommonPayloadReader payloadReader,
                           SourceANamespaceResolver namespaceResolver,
                           CompositionStage compositionStage,
                           IngestMetrics metrics) {
        super(payloadReader, compositionStage, metrics);
        this.namespaceResolver = namespaceResolver;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_A;
    }

    @Override
    protected String resolveNamespace(Message message, CommonPayload payload) {
        return namespaceResolver.resolve(header(message, CATEGORY_HEADER), payload.body());
    }
}
