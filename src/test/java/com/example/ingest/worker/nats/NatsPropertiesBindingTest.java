package com.example.ingest.worker.nats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class NatsPropertiesBindingTest {

    @EnableConfigurationProperties({NatsProperties.class, SourceProperties.class})
    static class Binding {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Binding.class)
            .withPropertyValues(
                    "app.nats.url=nats://example:4222",
                    "app.nats.sources.source-a.stream=SRC_A_EVENTS",
                    "app.nats.sources.source-a.subject=src-a.events.>",
                    "app.nats.sources.source-a.durable=ingest-worker-src-a",
                    "app.sources.enabled=source-a");

    @Test
    void bindsTheSourceMapAndTheEnabledList() {
        runner.run(context -> {
            NatsProperties nats = context.getBean(NatsProperties.class);
            assertThat(nats.url()).isEqualTo("nats://example:4222");
            assertThat(nats.sources()).containsOnlyKeys("source-a");
            assertThat(nats.sources().get("source-a").durable()).isEqualTo("ingest-worker-src-a");

            SourceProperties sources = context.getBean(SourceProperties.class);
            assertThat(sources.enabled()).containsExactly("source-a");
        });
    }
}
