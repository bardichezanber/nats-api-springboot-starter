package com.example.ingest.gateway;

/** Bridges gateway-received events into JetStream. */
public interface EventPublisher {

    void publish(GatewayEvent event);
}
