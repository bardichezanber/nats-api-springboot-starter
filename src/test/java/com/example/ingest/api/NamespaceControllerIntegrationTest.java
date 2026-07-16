package com.example.ingest.api;

import com.example.ingest.namespace.SourceKey;
import com.example.ingest.record.IngestedRecord;
import com.example.ingest.record.IngestedRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class NamespaceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IngestedRecordRepository records;

    @BeforeEach
    void cleanDatabase() {
        records.deleteAll();
    }

    @Test
    void listsEnabledNamespaces() throws Exception {
        mockMvc.perform(get("/api/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("alpha"))
                .andExpect(jsonPath("$[1]").value("beta"));
    }

    @Test
    void returnsRecordsForANamespaceWithRawJsonPayload() throws Exception {
        records.save(IngestedRecord.of("alpha", SourceKey.SOURCE_A, "orders.created",
                "e-1", "{\"amount\":42}", Instant.parse("2026-01-01T00:00:00Z"), Instant.now()));

        mockMvc.perform(get("/api/namespaces/alpha/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].namespaceKey").value("alpha"))
                .andExpect(jsonPath("$.content[0].eventType").value("orders.created"))
                .andExpect(jsonPath("$.content[0].dedupKey").value("e-1"))
                .andExpect(jsonPath("$.content[0].payload.amount").value(42))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void unknownNamespaceIs404() throws Exception {
        mockMvc.perform(get("/api/namespaces/nope/records"))
                .andExpect(status().isNotFound());
    }
}
