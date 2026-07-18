package com.example.ingest.worker.nats;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.SourceKey;
import com.example.ingest.worker.IngestMetrics;
import com.example.ingest.worker.composition.CompositionStage;
import com.example.ingest.worker.IngestResult;
import com.example.ingest.namespace.CommonPayloadReader;
import com.example.ingest.worker.source.SourceANamespaceResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceAConsumerTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final CompositionStage pipeline = mock(CompositionStage.class);
    private final SourceAConsumer consumer = new SourceAConsumer(
            new CommonPayloadReader(new ObjectMapper()), new SourceANamespaceResolver(), pipeline,
            new IngestMetrics(meterRegistry));

    @Test
    void resolvesNamespaceFromHeaderAndCommonFieldThenDelegates() {
        Message message = NatsMessage.builder()
                .subject("src-a.events.orders.created")
                .headers(new Headers().add(SourceAConsumer.CATEGORY_HEADER, "orders"))
                .data("""
                        {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z",
                         "region":"emea","data":{"amount":42}}
                        """)
                .build();
        when(pipeline.ingest(anyString(), any())).thenReturn(IngestResult.SAVED);

        IngestResult result = consumer.handle(message);

        assertThat(result).isEqualTo(IngestResult.SAVED);
        ArgumentCaptor<CommonEnvelope> captor = ArgumentCaptor.forClass(CommonEnvelope.class);
        verify(pipeline).ingest(eq("alpha"), captor.capture());
        CommonEnvelope envelope = captor.getValue();
        assertThat(envelope.source()).isEqualTo(SourceKey.SOURCE_A);
        assertThat(envelope.eventType()).isEqualTo("orders.created");
        assertThat(envelope.dedupKey()).isEqualTo("e-1");
        assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void rejectsMalformedBody() {
        Message message = NatsMessage.builder()
                .subject("src-a.events.orders.created")
                .headers(new Headers().add(SourceAConsumer.CATEGORY_HEADER, "orders"))
                .data("not json")
                .build();

        assertThatThrownBy(() -> consumer.handle(message))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedMessageIsTerminatedAndCountedAsPoison() {
        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn("src-a.events.orders.created");
        when(message.getHeaders()).thenReturn(new Headers().add(SourceAConsumer.CATEGORY_HEADER, "orders"));
        when(message.getData()).thenReturn("not json".getBytes(StandardCharsets.UTF_8));

        consumer.onMessage(message);

        verify(message).term();
        assertThat(meterRegistry.get("ingest.poison").tag("source", "source-a").counter().count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("ingest.handle").tag("source", "source-a").timer().count())
                .isEqualTo(1L);
    }

    @Test
    void rejectsUnexpectedSubject() {
        Message message = NatsMessage.builder()
                .subject("somewhere.else")
                .headers(new Headers().add(SourceAConsumer.CATEGORY_HEADER, "orders"))
                .data("""
                        {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","region":"emea"}
                        """)
                .build();

        assertThatThrownBy(() -> consumer.handle(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject");
    }
}
