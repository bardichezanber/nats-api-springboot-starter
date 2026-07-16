package com.example.ingest.record;

import com.example.ingest.namespace.SourceKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IngestedRecordRepositoryTest {

    @Autowired
    private IngestedRecordRepository repository;

    @Test
    void findsRecordsByNamespaceNewestFirst() {
        repository.save(record("alpha", "e-1", "2026-01-01T00:00:00Z"));
        repository.save(record("alpha", "e-2", "2026-01-02T00:00:00Z"));
        repository.save(record("beta", "e-3", "2026-01-03T00:00:00Z"));

        Page<IngestedRecord> page = repository
                .findByNamespaceKeyOrderByOccurredAtDesc("alpha", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(IngestedRecord::getDedupKey)
                .containsExactly("e-2", "e-1");
    }

    private static IngestedRecord record(String namespaceKey, String dedupKey, String occurredAt) {
        return IngestedRecord.of(namespaceKey, SourceKey.SOURCE_A, "orders.created",
                dedupKey, "{\"amount\":1}", Instant.parse(occurredAt), Instant.now());
    }
}
