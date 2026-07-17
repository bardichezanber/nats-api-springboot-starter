package com.example.ingest.gateway;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;

@Configuration
@Profile("gateway")
@EnableScheduling
public class GatewayConfig {

    @Bean(destroyMethod = "close")
    public Connection natsConnection(Environment env) throws IOException, InterruptedException {
        Options options = Options.builder()
                .server(env.getProperty("app.nats.url", "nats://localhost:4222"))
                .connectionName("ingest-gateway")
                .build();
        return Nats.connect(options);
    }
}
