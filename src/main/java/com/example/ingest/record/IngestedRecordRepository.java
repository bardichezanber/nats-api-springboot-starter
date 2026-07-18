package com.example.ingest.record;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IngestedRecordRepository extends JpaRepository<IngestedRecord, Long> {

    Page<IngestedRecord> findByNamespaceKeyOrderByOccurredAtDesc(String namespaceKey, Pageable pageable);

    /** First keyset window: newest first, id as the tiebreak for equal timestamps. */
    List<IngestedRecord> findByNamespaceKeyOrderByOccurredAtDescIdDesc(String namespaceKey, Pageable pageable);

    /**
     * Keyset window after the (occurredBefore, idBefore) cursor — constant
     * cost at any depth, unlike offset paging, and served by the
     * (namespace_key, occurred_at) index.
     */
    @Query("""
            select r from IngestedRecord r
            where r.namespaceKey = :namespaceKey
              and (r.occurredAt < :occurredBefore
                or (r.occurredAt = :occurredBefore and r.id < :idBefore))
            order by r.occurredAt desc, r.id desc
            """)
    List<IngestedRecord> findWindowBefore(@Param("namespaceKey") String namespaceKey,
                                          @Param("occurredBefore") Instant occurredBefore,
                                          @Param("idBefore") long idBefore,
                                          Pageable pageable);
}
