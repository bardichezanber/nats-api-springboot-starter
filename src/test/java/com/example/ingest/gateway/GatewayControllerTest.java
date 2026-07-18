package com.example.ingest.gateway;

import com.example.ingest.namespace.SourceKey;
import com.example.ingest.namespace.CommonPayloadReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GatewayControllerTest {

    private final List<GatewayEvent> published = new ArrayList<>();
    private boolean authenticated = true;

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new GatewayController(
            request -> authenticated,
            new CommonPayloadReader(new ObjectMapper()),
            published::add)).build();

    private static final String VALID_BODY = """
            {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","data":{"amount":42}}
            """;

    @Test
    void acceptsAndPublishesAValidEvent() throws Exception {
        mvc.perform(post("/gateway/events/orders.created")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.dedupKey").value("e-1"));

        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.source()).isEqualTo(SourceKey.SOURCE_HTTP);
            assertThat(event.eventType()).isEqualTo("orders.created");
            assertThat(event.namespaceKey()).isEqualTo("alpha");
            assertThat(event.dedupKey()).isEqualTo("e-1");
            assertThat(event.body().path("data").path("amount").asInt()).isEqualTo(42);
        });
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        authenticated = false;

        mvc.perform(post("/gateway/events/orders.created")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsMissingNamespaceHeader() throws Exception {
        mvc.perform(post("/gateway/events/orders.created")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsPayloadWithoutCommonFields() throws Exception {
        mvc.perform(post("/gateway/events/orders.created")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{}}"))
                .andExpect(status().isBadRequest());
        assertThat(published).isEmpty();
    }

    @Test
    void rejectsWildcardEventTypes() throws Exception {
        mvc.perform(post("/gateway/events/orders.>")
                        .header("X-Namespace", "alpha")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
        assertThat(published).isEmpty();
    }
}
