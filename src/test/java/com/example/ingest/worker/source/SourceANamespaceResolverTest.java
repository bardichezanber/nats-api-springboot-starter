package com.example.ingest.worker.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceANamespaceResolverTest {

    private final SourceANamespaceResolver resolver = new SourceANamespaceResolver();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void combinesCategoryHeaderAndCommonRegionField() throws JsonProcessingException {
        assertThat(resolver.resolve("orders", objectMapper.readTree("{\"region\":\"emea\"}")))
                .isEqualTo("alpha");
        assertThat(resolver.resolve("orders", objectMapper.readTree("{\"region\":\"apac\"}")))
                .isEqualTo("beta");
    }

    @Test
    void pinsTheFullRoutingTable() throws JsonProcessingException {
        // Append-only. Every known (category, region) -> namespace row is listed;
        // adding a namespace adds rows, editing a row changes another namespace's traffic.
        assertThat(resolver.resolve("orders", region("emea"))).isEqualTo("alpha");
        assertThat(resolver.resolve("payments", region("emea"))).isEqualTo("alpha");
        assertThat(resolver.resolve("orders", region("apac"))).isEqualTo("beta");
    }

    private JsonNode region(String region) throws JsonProcessingException {
        return objectMapper.readTree("{\"region\":\"" + region + "\"}");
    }

    @Test
    void rejectsMissingCategoryHeader() throws JsonProcessingException {
        var body = objectMapper.readTree("{\"region\":\"emea\"}");

        assertThatThrownBy(() -> resolver.resolve(null, body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Category");
    }

    @Test
    void rejectsMissingRegionField() throws JsonProcessingException {
        var body = objectMapper.readTree("{}");

        assertThatThrownBy(() -> resolver.resolve("orders", body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("region");
    }

    @Test
    void rejectsUnmappedCombination() throws JsonProcessingException {
        var body = objectMapper.readTree("{\"region\":\"mars\"}");

        assertThatThrownBy(() -> resolver.resolve("orders", body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no namespace mapping");
    }
}
