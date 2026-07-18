package com.example.ingest.worker.composition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CompositionRepositoryTest {

    @Autowired
    private CompositionStateRepository states;

    @Autowired
    private CompositionPartRepository parts;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void cleanDatabase() {
        parts.deleteAll();
        states.deleteAll();
    }

    private CompositionState pending(String key, Instant createdAt) {
        return new CompositionState(key, "alpha", CompositionStatus.PENDING,
                "x.ready,y.ready", "ready.composed", createdAt.plusSeconds(900), createdAt);
    }

    @Test
    @Transactional
    void markComposedWinsOnceAndOnlyOnce() {
        states.saveAndFlush(pending("alpha:c-1", NOW));

        assertThat(states.markComposed("alpha:c-1", NOW, CompositionStatus.COMPOSED, CompositionStatus.PENDING))
                .isEqualTo(1);
        assertThat(states.markComposed("alpha:c-1", NOW, CompositionStatus.COMPOSED, CompositionStatus.PENDING))
                .isZero();
    }

    @Test
    @Transactional
    void markExpiredOnlyTouchesPendingStates() {
        states.saveAndFlush(pending("alpha:c-2", NOW));
        states.markComposed("alpha:c-2", NOW, CompositionStatus.COMPOSED, CompositionStatus.PENDING);

        assertThat(states.markExpired("alpha:c-2", CompositionStatus.EXPIRED, CompositionStatus.PENDING))
                .isZero();
    }

    @Test
    void duplicatePartHitsTheUniqueConstraint() {
        parts.saveAndFlush(new CompositionPart("alpha:c-3", "x.ready", "SOURCE_A", "x.ready",
                "{}", NOW, NOW));

        assertThatThrownBy(() -> parts.saveAndFlush(
                new CompositionPart("alpha:c-3", "x.ready", "SOURCE_A", "x.ready", "{}", NOW, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void retentionDeletesTerminalStatesAndOrphanPartsButKeepsPending() {
        Instant old = NOW.minusSeconds(864000);
        states.saveAndFlush(pending("alpha:old-composed", old));
        states.markComposed("alpha:old-composed", old, CompositionStatus.COMPOSED, CompositionStatus.PENDING);
        states.saveAndFlush(pending("alpha:old-pending", old));
        parts.saveAndFlush(new CompositionPart("alpha:old-composed", "x.ready", "SOURCE_A", "x.ready",
                "{}", old, old));
        parts.saveAndFlush(new CompositionPart("alpha:old-pending", "x.ready", "SOURCE_A", "x.ready",
                "{}", old, old));

        int deleted = states.deleteTerminalOlderThan(CompositionStatus.PENDING, NOW);
        int orphans = parts.deleteOrphansOlderThan(NOW);

        assertThat(deleted).isEqualTo(1);
        assertThat(orphans).isEqualTo(1);
        assertThat(states.findById("alpha:old-pending")).isPresent();
        assertThat(parts.findByCorrelationKey("alpha:old-pending")).hasSize(1);
    }
}
