package com.example.ingest.gateway;

import com.example.ingest.namespace.MessageHeaders;
import com.example.ingest.namespace.SourceKey;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Publishes to {@code src-http.events.*} / {@code src-ftp.events.*} with
 * {@code Nats-Msg-Id = dedupKey} (JetStream first-line dedup; the worker's
 * DB ledger stays the backstop) and the namespace in {@code X-Namespace}.
 */
@Component
@Profile("gateway")
public class NatsEventPublisher implements EventPublisher {

    private static final Set<SourceKey> GATEWAY_SOURCES = Set.of(SourceKey.SOURCE_HTTP, SourceKey.SOURCE_FTP);

    private final Connection connection;
    private final GatewayMetrics metrics;

    public NatsEventPublisher(Connection connection, GatewayMetrics metrics) {
        this.connection = connection;
        this.metrics = metrics;
    }

    @Override
    public void publish(GatewayEvent event) {
        if (!GATEWAY_SOURCES.contains(event.source())) {
            throw new IllegalArgumentException("no gateway subject for source " + event.source());
        }
        NatsMessage message = NatsMessage.builder()
                .subject(event.source().subjectPrefix() + event.eventType())
                .headers(new Headers()
                        .add("Nats-Msg-Id", event.dedupKey())
                        .add(MessageHeaders.NAMESPACE, event.namespaceKey()))
                .data(event.body().toString().getBytes(StandardCharsets.UTF_8))
                .build();
        try {
            connection.jetStream().publish(message);
            metrics.published(event.source());
        } catch (IOException | JetStreamApiException e) {
            metrics.publishFailed(event.source());
            throw new IllegalStateException("failed to publish " + message.getSubject(), e);
        }
    }
}
