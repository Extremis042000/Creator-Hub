package com.extremis.hub.repository;

import com.extremis.hub.domain.AdminGrant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminGrantRepository extends JpaRepository<AdminGrant, UUID> {
}
