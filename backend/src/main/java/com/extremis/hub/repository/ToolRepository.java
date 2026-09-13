package com.extremis.hub.repository;

import com.extremis.hub.domain.Tool;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolRepository extends JpaRepository<Tool, UUID> {
}
