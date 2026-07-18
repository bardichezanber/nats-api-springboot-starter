package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.namespace.MessageHeaders;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.source.SourceBNamespaceResolver;
import io.nats.client.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source B: subjects {@code src-b.events.<eventType>}; the upstream contract
 * puts the namespace directly in the {@code X-Namespace} header.
 */
@Component
@Profile("worker")
public class SourceBConsumer extends BaseSourceConsumer {

    private final SourceBNamespaceResolver namespaceResolver;

    public SourceBConsumer(CommonPayloadReader payloadReader,
                           SourceBNamespaceResolver namespaceResolver,
                           CompositionStage compositionStage,
                           IngestMetrics metrics) {
        super(payloadReader, compositionStage, metrics);
        this.namespaceResolver = namespaceResolver;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_B;
    }

    @Override
    protected String resolveNamespace(Message message, CommonPayload payload) {
        return namespaceResolver.resolve(header(message, MessageHeaders.NAMESPACE));
    }
}
