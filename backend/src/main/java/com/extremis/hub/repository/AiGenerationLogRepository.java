package com.extremis.hub.repository;

import com.extremis.hub.domain.AiGenerationLog;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationLogRepository extends JpaRepository<AiGenerationLog, UUID> {

    List<AiGenerationLog> findByCreatedAtGreaterThanEqual(Instant since);
}
