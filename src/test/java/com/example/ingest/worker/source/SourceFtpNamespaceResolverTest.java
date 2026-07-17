package com.example.ingest.worker.source;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceFtpNamespaceResolverTest {

    private final SourceFtpNamespaceResolver resolver = new SourceFtpNamespaceResolver();

    @Test
    void usesTheDeclaredNamespace() {
        assertThat(resolver.resolve("alpha")).isEqualTo("alpha");
        assertThat(resolver.resolve("  beta ")).isEqualTo("beta");
    }

    @Test
    void rejectsMissingHeader() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Namespace");
        assertThatThrownBy(() -> resolver.resolve(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("X-Namespace");
    }
}
