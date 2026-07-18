package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonPayload;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.namespace.MessageHeaders;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.source.SourceFtpNamespaceResolver;
import io.nats.client.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Source FTP: subjects {@code src-ftp.events.<eventType>}, published by the
 * gateway's file poller; the namespace is declared in the
 * {@code X-Namespace} header.
 */
@Component
@Profile("worker")
public class SourceFtpConsumer extends BaseSourceConsumer {

    private final SourceFtpNamespaceResolver namespaceResolver;

    public SourceFtpConsumer(CommonPayloadReader payloadReader,
                             SourceFtpNamespaceResolver namespaceResolver,
                             CompositionStage compositionStage,
                             IngestMetrics metrics) {
        super(payloadReader, compositionStage, metrics);
        this.namespaceResolver = namespaceResolver;
    }

    @Override
    public SourceKey source() {
        return SourceKey.SOURCE_FTP;
    }

    @Override
    protected String resolveNamespace(Message message, CommonPayload payload) {
        return namespaceResolver.resolve(header(message, MessageHeaders.NAMESPACE));
    }
}
