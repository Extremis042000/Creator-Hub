package com.extremis.hub.repository;

import com.extremis.hub.domain.GeneratedResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneratedResultRepository extends JpaRepository<GeneratedResult, UUID> {
    Optional<GeneratedResult> findByShareToken(String shareToken);
}
