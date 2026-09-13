package com.extremis.hub.repository;

import com.extremis.hub.domain.ToolUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolUsageRepository extends JpaRepository<ToolUsage, UUID> {
}
