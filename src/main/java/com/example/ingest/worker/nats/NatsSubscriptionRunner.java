package com.example.ingest.worker.nats;

import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.MessageConsumer;
import io.nats.client.api.AckPolicy;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts one durable <em>pull</em> consumer per enabled source. Pull is the
 * scalable model: every worker pod fetches from the same durable, so adding
 * replicas divides the work — deployments scale per source on consumer lag.
 * Streams and consumers are auto-created if missing — convenient for local
 * dev; provision them out-of-band in production.
 *
 * <p>Note for pre-existing environments: the durables used to be push
 * consumers. A consumer cannot change type in place — delete the old push
 * consumer (e.g. {@code nats consumer rm}) before starting this version.
 */
@Component
@Profile("worker")
public class NatsSubscriptionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NatsSubscriptionRunner.class);

    private final Connection connection;
    private final SourceRegistry registry;
    private final List<MessageConsumer> running = new ArrayList<>();

    public NatsSubscriptionRunner(Connection connection, SourceRegistry registry) {
        this.connection = connection;
        this.registry = registry;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, JetStreamApiException {
        for (SourceRegistry.Subscription subscription : registry.subscriptions()) {
            ensureStream(subscription.config());
            running.add(startConsuming(subscription));
            log.info("consuming source {} from stream {} (durable {})",
                    subscription.source().key(), subscription.config().stream(),
                    subscription.config().durable());
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

    private MessageConsumer startConsuming(SourceRegistry.Subscription subscription)
            throws IOException, JetStreamApiException {
        NatsProperties.SourceConfig config = subscription.config();
        connection.jetStreamManagement().addOrUpdateConsumer(config.stream(),
                ConsumerConfiguration.builder()
                        .durable(config.durable())
                        .filterSubject(config.subject())
                        .ackPolicy(AckPolicy.Explicit)
                        .build());
        ConsumerContext context = connection.getConsumerContext(config.stream(), config.durable());
        return context.consume(subscription.consumer()::onMessage);
    }

    @PreDestroy
    void stop() {
        for (MessageConsumer consumer : running) {
            try {
                consumer.stop();
            } catch (Exception e) {
                log.warn("failed to stop consumer cleanly", e);
            }
        }
    }
}
