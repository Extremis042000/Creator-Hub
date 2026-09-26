package com.extremis.hub.repository;

import com.extremis.hub.domain.AiGenerationLog;
import com.extremis.hub.domain.OutcomeSignal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, UUID> {

    List<AiGenerationLog> findByCreatedAtGreaterThanEqual(Instant since);

    /**
     * Phase 37: records a signal only if none is set yet -- first
     * signal wins, so a REGENERATED check landing after the user
     * already copied or refined the same result can't clobber that.
     * Returns the number of rows updated (0 or 1) so the caller can
     * tell whether this call actually recorded anything.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AiGenerationLog l SET l.outcomeSignal = :signal WHERE l.id = :id AND l.outcomeSignal IS NULL")
    int recordOutcomeSignalIfAbsent(@Param("id") UUID id, @Param("signal") OutcomeSignal signal);

    /** Phase 37: COPIED is the strongest explicit signal a user can give -- allowed to overwrite an earlier inferred one. */
    @Modifying
    @Transactional
    @Query("UPDATE AiGenerationLog l SET l.outcomeSignal = :signal WHERE l.id = :id")
    int recordOutcomeSignal(@Param("id") UUID id, @Param("signal") OutcomeSignal signal);
}
