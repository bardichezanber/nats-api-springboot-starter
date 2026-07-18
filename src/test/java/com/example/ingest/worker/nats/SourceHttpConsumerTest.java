package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.worker.source.CommonPayloadReader;
import com.example.ingest.worker.source.SourceHttpNamespaceResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceHttpConsumerTest {

    private final CompositionStage pipeline = mock(CompositionStage.class);
    private final SourceHttpConsumer consumer = new SourceHttpConsumer(
            new CommonPayloadReader(new ObjectMapper()), new SourceHttpNamespaceResolver(), pipeline,
            new IngestMetrics(new SimpleMeterRegistry()));

    @Test
    void usesTheDeclaredNamespaceFromTheHeader() {
        Message message = NatsMessage.builder()
                .subject("src-http.events.orders.created")
                .headers(new Headers().add(SourceHttpConsumer.NAMESPACE_HEADER, "alpha"))
                .data("""
                        {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z",
                         "data":{"amount":42}}
                        """)
                .build();
        when(pipeline.ingest(anyString(), any())).thenReturn(IngestResult.SAVED);

        IngestResult result = consumer.handle(message);

        assertThat(result).isEqualTo(IngestResult.SAVED);
        ArgumentCaptor<CommonEnvelope> captor = ArgumentCaptor.forClass(CommonEnvelope.class);
        verify(pipeline).ingest(eq("alpha"), captor.capture());
        CommonEnvelope envelope = captor.getValue();
        assertThat(envelope.source()).isEqualTo(SourceKey.SOURCE_HTTP);
        assertThat(envelope.eventType()).isEqualTo("orders.created");
        assertThat(envelope.dedupKey()).isEqualTo("e-1");
    }

    @Test
    void rejectsMissingNamespaceHeader() {
        Message message = NatsMessage.builder()
                .subject("src-http.events.orders.created")
                .data("""
                        {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z"}
                        """)
                .build();

        assertThatThrownBy(() -> consumer.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SourceHttpConsumer.NAMESPACE_HEADER);
    }
}
