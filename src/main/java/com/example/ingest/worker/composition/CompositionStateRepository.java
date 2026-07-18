package com.example.ingest.worker.composition;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CompositionStateRepository extends JpaRepository<CompositionState, String> {

    /**
     * Locks the state row so parts of the same correlation serialize — this
     * is what makes "the last part's transaction composes" safe when two
     * parts arrive at the same moment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CompositionState> findWithLockingByCorrelationKey(String correlationKey);

    /** Guarded transition PENDING -> COMPOSED; 0 rows = another transaction won. */
    @Modifying
    @Query("update CompositionState s set s.status = :composed, s.completedAt = :now "
            + "where s.correlationKey = :key and s.status = :pending")
    int markComposed(@Param("key") String key, @Param("now") Instant now,
                     @Param("composed") CompositionStatus composed,
                     @Param("pending") CompositionStatus pending);

    /** Guarded transition PENDING -> EXPIRED; 0 rows = composed or already expired. */
    @Modifying
    @Query("update CompositionState s set s.status = :expired "
            + "where s.correlationKey = :key and s.status = :pending")
    int markExpired(@Param("key") String key,
                    @Param("expired") CompositionStatus expired,
                    @Param("pending") CompositionStatus pending);

    List<CompositionState> findByStatusAndDeadlineAtBefore(CompositionStatus status, Instant deadline);

    long countByStatus(CompositionStatus status);

    /** Retention: drop terminal states past the cutoff; PENDING rows are never touched. */
    @Modifying
    @Query("delete from CompositionState s where s.status <> :keep and s.createdAt < :cutoff")
    int deleteTerminalOlderThan(@Param("keep") CompositionStatus keep, @Param("cutoff") Instant cutoff);
}
