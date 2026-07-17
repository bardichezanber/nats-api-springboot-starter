package com.example.ingest.gateway;

import com.example.ingest.namespace.SourceKey;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Publishes to {@code src-http.events.*} / {@code src-ftp.events.*} with
 * {@code Nats-Msg-Id = dedupKey} (JetStream first-line dedup; the worker's
 * DB ledger stays the backstop) and the namespace in {@code X-Namespace}.
 */
@Component
@Profile("gateway")
public class NatsEventPublisher implements EventPublisher {

    static final Map<SourceKey, String> SUBJECT_PREFIXES = Map.of(
            SourceKey.SOURCE_HTTP, "src-http.events.",
            SourceKey.SOURCE_FTP, "src-ftp.events.");

    private final Connection connection;
    private final GatewayMetrics metrics;

    public NatsEventPublisher(Connection connection, GatewayMetrics metrics) {
        this.connection = connection;
        this.metrics = metrics;
    }

    @Override
    public void publish(GatewayEvent event) {
        String prefix = SUBJECT_PREFIXES.get(event.source());
        if (prefix == null) {
            throw new IllegalArgumentException("no gateway subject for source " + event.source());
        }
        NatsMessage message = NatsMessage.builder()
                .subject(prefix + event.eventType())
                .headers(new Headers()
                        .add("Nats-Msg-Id", event.dedupKey())
                        .add("X-Namespace", event.namespaceKey()))
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
