package com.example.ingest.worker.composition;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CompositionPartRepository extends JpaRepository<CompositionPart, Long> {

    boolean existsByCorrelationKeyAndPartKey(String correlationKey, String partKey);

    List<CompositionPart> findByCorrelationKey(String correlationKey);

    /** Parts whose state row was already removed by retention. */
    @Modifying
    @Query("delete from CompositionPart p where p.createdAt < :cutoff and p.correlationKey not in "
            + "(select s.correlationKey from CompositionState s)")
    int deleteOrphansOlderThan(@Param("cutoff") Instant cutoff);
}
