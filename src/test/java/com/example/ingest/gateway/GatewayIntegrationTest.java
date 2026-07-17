package com.example.ingest.gateway;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the real gateway profile: proves the DB-free autoconfigure excludes
 * hold, the auth token is enforced, and a valid request reaches JetStream
 * with the dedup headers.
 */
@SpringBootTest(properties = "app.gateway.token=test-token")
@ActiveProfiles("gateway")
@AutoConfigureMockMvc
class GatewayIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private Connection connection;

    private final JetStream jetStream = mock(JetStream.class);

    @BeforeEach
    void stubJetStream() throws IOException {
        when(connection.jetStream()).thenReturn(jetStream);
    }

    @Test
    void endToEndPublishWithDedupHeaders() throws Exception {
        mvc.perform(post("/gateway/events/orders.created")
                        .header("Authorization", "Bearer test-token")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e-1\",\"occurredAt\":\"2026-01-01T00:00:00Z\",\"data\":{}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.dedupKey").value("e-1"));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(jetStream).publish(captor.capture());
        Message message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("src-http.events.orders.created");
        assertThat(message.getHeaders().getFirst("Nats-Msg-Id")).isEqualTo("e-1");
        assertThat(message.getHeaders().getFirst("X-Namespace")).isEqualTo("alpha");
    }

    @Test
    void rejectsWrongToken() throws Exception {
        mvc.perform(post("/gateway/events/orders.created")
                        .header("Authorization", "Bearer wrong")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"e-1\",\"occurredAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnauthorized());
    }
}
