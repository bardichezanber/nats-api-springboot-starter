package com.example.ingest.namespace;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamespaceRegistryTest {

    private final NamespacePolicy alpha = policy("alpha");
    private final NamespacePolicy beta = policy("beta");

    @Test
    void exposesOnlyEnabledNamespacesInConfigOrder() {
        NamespaceRegistry registry = new NamespaceRegistry(
                List.of(alpha, beta), new NamespaceProperties(List.of("beta", "alpha")));

        assertThat(registry.enabledKeys()).containsExactly("beta", "alpha");
        assertThat(registry.find("alpha")).contains(alpha);
        assertThat(registry.find("beta")).contains(beta);
    }

    @Test
    void findReturnsEmptyForKnownButDisabledNamespace() {
        NamespaceRegistry registry = new NamespaceRegistry(
                List.of(alpha, beta), new NamespaceProperties(List.of("alpha")));

        assertThat(registry.find("beta")).isEmpty();
        assertThat(registry.isKnown("beta")).isTrue();
        assertThat(registry.isKnown("gamma")).isFalse();
    }

    @Test
    void failsFastWhenAnEnabledNamespaceHasNoPolicy() {
        assertThatThrownBy(() -> new NamespaceRegistry(
                List.of(alpha), new NamespaceProperties(List.of("alpha", "gamma"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gamma");
    }

    private static NamespacePolicy policy(String key) {
        return new NamespacePolicy() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public JsonNode parse(CommonEnvelope envelope) {
                return envelope.body();
            }
        };
    }
}
