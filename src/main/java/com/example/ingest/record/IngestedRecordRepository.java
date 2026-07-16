package com.example.ingest.record;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestedRecordRepository extends JpaRepository<IngestedRecord, Long> {

    Page<IngestedRecord> findByNamespaceKeyOrderByOccurredAtDesc(String namespaceKey, Pageable pageable);
}
