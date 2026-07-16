package com.example.ingest.worker.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceBNamespaceResolverTest {

    private final SourceBNamespaceResolver resolver = new SourceBNamespaceResolver();

    @Test
    void usesTheDeclaredNamespaceAsIs() {
        assertThat(resolver.resolve("beta")).isEqualTo("beta");
        assertThat(resolver.resolve(" alpha ")).isEqualTo("alpha");
    }

    @Test
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Namespace");
        assertThatThrownBy(() -> resolver.resolve(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
