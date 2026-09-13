package com.extremis.hub.repository;

import com.extremis.hub.domain.Tool;
import com.extremis.hub.domain.ToolType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolRepository extends JpaRepository<Tool, UUID> {
    Optional<Tool> findByToolType(ToolType toolType);
}
