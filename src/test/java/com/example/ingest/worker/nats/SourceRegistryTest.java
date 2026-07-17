package com.example.ingest.worker.nats;

import com.example.ingest.namespace.SourceKey;
import io.nats.client.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceRegistryTest {

    private static SourceConsumer consumer(SourceKey key) {
        return new SourceConsumer() {
            @Override
            public SourceKey source() {
                return key;
            }

            @Override
            public void onMessage(Message message) {
            }
        };
    }

    private static NatsProperties.SourceConfig config(String name) {
        return new NatsProperties.SourceConfig("S_" + name, name + ".events.>", "ingest-worker-" + name);
    }

    private final NatsProperties natsProperties = new NatsProperties("nats://localhost:4222",
            Map.of("source-a", config("src-a"), "source-b", config("src-b")));
    private final List<SourceConsumer> consumers =
            List.of(consumer(SourceKey.SOURCE_A), consumer(SourceKey.SOURCE_B));

    @Test
    void buildsOneSubscriptionPerEnabledSourceInConfigOrder() {
        SourceRegistry registry = new SourceRegistry(
                new SourceProperties(List.of("source-b", "source-a")), natsProperties, consumers);

        assertThat(registry.subscriptions()).hasSize(2);
        assertThat(registry.subscriptions().get(0).source()).isEqualTo(SourceKey.SOURCE_B);
        assertThat(registry.subscriptions().get(0).config().stream()).isEqualTo("S_src-b");
        assertThat(registry.subscriptions().get(1).source()).isEqualTo(SourceKey.SOURCE_A);
    }

    @Test
    void disabledSourcesAreNotSubscribed() {
        SourceRegistry registry = new SourceRegistry(
                new SourceProperties(List.of("source-a")), natsProperties, consumers);

        assertThat(registry.subscriptions()).hasSize(1);
        assertThat(registry.subscriptions().get(0).source()).isEqualTo(SourceKey.SOURCE_A);
    }

    @Test
    void failsFastOnUnknownSourceKey() {
        assertThatThrownBy(() -> new SourceRegistry(
                new SourceProperties(List.of("source-nope")), natsProperties, consumers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source-nope");
    }

    @Test
    void failsFastWhenEnabledSourceHasNoConsumerBean() {
        assertThatThrownBy(() -> new SourceRegistry(
                new SourceProperties(List.of("source-a")), natsProperties,
                List.of(consumer(SourceKey.SOURCE_B))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("consumer");
    }

    @Test
    void failsFastWhenEnabledSourceHasNoNatsConfig() {
        NatsProperties onlyA = new NatsProperties("nats://localhost:4222", Map.of("source-a", config("src-a")));

        assertThatThrownBy(() -> new SourceRegistry(
                new SourceProperties(List.of("source-a", "source-b")), onlyA, consumers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source-b");
    }
}
