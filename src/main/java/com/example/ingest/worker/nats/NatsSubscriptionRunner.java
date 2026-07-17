package com.example.ingest.worker.nats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Wires durable push subscriptions for every enabled source when the app
 * runs in the worker role. Streams are auto-created if missing — convenient
 * for local dev; provision them out-of-band in production.
 */
@Component
@Profile("worker")
public class NatsSubscriptionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NatsSubscriptionRunner.class);

    private final Connection connection;
    private final SourceRegistry registry;

    public NatsSubscriptionRunner(Connection connection, SourceRegistry registry) {
        this.connection = connection;
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, JetStreamApiException {
        JetStream jetStream = connection.jetStream();
        Dispatcher dispatcher = connection.createDispatcher();
        for (SourceRegistry.Subscription subscription : registry.subscriptions()) {
            ensureStream(subscription.config());
            subscribe(jetStream, dispatcher, subscription.config(), subscription.consumer()::onMessage);
            log.info("subscribed source {} ({})", subscription.source().key(), subscription.config().subject());
        }
    }

    private void ensureStream(NatsProperties.SourceConfig config) throws IOException, JetStreamApiException {
        JetStreamManagement management = connection.jetStreamManagement();
        try {
            management.getStreamInfo(config.stream());
        } catch (JetStreamApiException e) {
            log.info("stream {} not found, creating it for subject {}", config.stream(), config.subject());
            management.addStream(StreamConfiguration.builder()
                    .name(config.stream())
                    .subjects(config.subject())
                    .storageType(StorageType.File)
                    .build());
        }
    }

    private void subscribe(JetStream jetStream, Dispatcher dispatcher,
                           NatsProperties.SourceConfig config, MessageHandler handler)
            throws IOException, JetStreamApiException {
        PushSubscribeOptions options = PushSubscribeOptions.builder()
                .stream(config.stream())
                .durable(config.durable())
                .build();
        jetStream.subscribe(config.subject(), dispatcher, handler, false, options);
    }
}
