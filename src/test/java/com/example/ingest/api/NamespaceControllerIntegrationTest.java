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
import org.springframework.test.web.servlet.MvcResult;

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

    @Test
    void windowPagesNewestFirstFollowingTheKeysetCursor() throws Exception {
        // Two records share occurredAt so the second page proves the id tiebreak.
        records.save(IngestedRecord.of("alpha", SourceKey.SOURCE_A, "orders.created",
                "e-old", "{}", Instant.parse("2026-01-01T10:00:00Z"), Instant.now()));
        records.save(IngestedRecord.of("alpha", SourceKey.SOURCE_A, "orders.created",
                "e-tie-low", "{}", Instant.parse("2026-01-01T11:00:00Z"), Instant.now()));
        records.save(IngestedRecord.of("alpha", SourceKey.SOURCE_A, "orders.created",
                "e-tie-high", "{}", Instant.parse("2026-01-01T11:00:00Z"), Instant.now()));

        MvcResult firstPage = mockMvc.perform(get("/api/namespaces/alpha/records/window")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].dedupKey").value("e-tie-high"))
                .andExpect(jsonPath("$.records[1].dedupKey").value("e-tie-low"))
                .andExpect(jsonPath("$.nextOccurredBefore").value("2026-01-01T11:00:00Z"))
                .andReturn();
        String nextIdBefore = com.jayway.jsonpath.JsonPath
                .read(firstPage.getResponse().getContentAsString(), "$.nextIdBefore").toString();

        mockMvc.perform(get("/api/namespaces/alpha/records/window")
                        .param("size", "2")
                        .param("occurredBefore", "2026-01-01T11:00:00Z")
                        .param("idBefore", nextIdBefore))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].dedupKey").value("e-old"))
                .andExpect(jsonPath("$.nextOccurredBefore").value("2026-01-01T10:00:00Z"));
    }

    @Test
    void windowIsEmptyPastTheLastRecord() throws Exception {
        records.save(IngestedRecord.of("alpha", SourceKey.SOURCE_A, "orders.created",
                "e-1", "{}", Instant.parse("2026-01-01T10:00:00Z"), Instant.now()));

        mockMvc.perform(get("/api/namespaces/alpha/records/window")
                        .param("occurredBefore", "2026-01-01T10:00:00Z")
                        .param("idBefore", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(0))
                .andExpect(jsonPath("$.nextOccurredBefore").doesNotExist())
                .andExpect(jsonPath("$.nextIdBefore").doesNotExist());
    }

    @Test
    void windowRejectsHalfACursor() throws Exception {
        mockMvc.perform(get("/api/namespaces/alpha/records/window")
                        .param("occurredBefore", "2026-01-01T10:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void windowUnknownNamespaceIs404() throws Exception {
        mockMvc.perform(get("/api/namespaces/nope/records/window"))
                .andExpect(status().isNotFound());
    }
}
