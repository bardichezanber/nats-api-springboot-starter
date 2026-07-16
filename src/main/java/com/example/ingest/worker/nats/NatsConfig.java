package com.example.ingest.worker.nats;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;

@Configuration
@Profile("worker")
public class NatsConfig {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(NatsProperties properties) throws IOException, InterruptedException {
        Options options = Options.builder()
                .server(properties.url())
                .connectionName("ingest-worker")
                .build();
        return Nats.connect(options);
    }
}
