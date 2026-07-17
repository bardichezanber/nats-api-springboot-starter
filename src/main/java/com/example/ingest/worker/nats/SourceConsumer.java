package com.example.ingest.worker.nats;

import com.example.ingest.namespace.SourceKey;
import io.nats.client.Message;

/**
 * One NATS-consuming bean per source. The registry discovers all
 * implementations and subscribes only the enabled ones.
 */
public interface SourceConsumer {

    SourceKey source();

    void onMessage(Message message);
}
