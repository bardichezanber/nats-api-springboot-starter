package com.example.ingest.worker.source;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Source A cannot name the namespace directly: it is derived from a
 * source-specific part of the NATS message (the {@code X-Category} header)
 * combined with a field inside the common payload ({@code region}, whose
 * position is namespace-independent). Everything needed for the decision is
 * on the message itself — no external lookup.
 *
 * <p>The mapping below is an example; replace it with real business rules.
 */
@Component
public class SourceANamespaceResolver {

    private static final Map<CategoryRegion, String> MAPPING = Map.of(
            new CategoryRegion("orders", "emea"), "alpha",
            new CategoryRegion("payments", "emea"), "alpha",
            new CategoryRegion("orders", "apac"), "beta");

    public String resolve(String category, JsonNode body) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("source A message is missing the X-Category header");
        }
        JsonNode region = body.path("region");
        if (!region.isTextual()) {
            throw new IllegalArgumentException("source A message is missing the common 'region' field");
        }
        String namespace = MAPPING.get(new CategoryRegion(category, region.asText()));
        if (namespace == null) {
            throw new IllegalArgumentException(
                    "no namespace mapping for category=" + category + ", region=" + region.asText());
        }
        return namespace;
    }

    private record CategoryRegion(String category, String region) {
    }
}
