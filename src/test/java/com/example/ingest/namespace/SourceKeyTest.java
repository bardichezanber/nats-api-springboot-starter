package com.example.ingest.namespace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceKeyTest {

    @Test
    void everyConstantRoundTripsThroughItsConfigKey() {
        for (SourceKey source : SourceKey.values()) {
            assertThat(SourceKey.fromKey(source.key())).isEqualTo(source);
        }
    }

    @Test
    void configKeysAreKebabCase() {
        assertThat(SourceKey.SOURCE_A.key()).isEqualTo("source-a");
        assertThat(SourceKey.SOURCE_B.key()).isEqualTo("source-b");
    }

    @Test
    void rejectsUnknownConfigKey() {
        assertThatThrownBy(() -> SourceKey.fromKey("source-nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source-nope");
    }
}
