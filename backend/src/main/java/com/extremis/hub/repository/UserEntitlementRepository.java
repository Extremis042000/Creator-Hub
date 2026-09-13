package com.extremis.hub.repository;

import com.extremis.hub.domain.UserEntitlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, UUID> {
    Optional<UserEntitlement> findByUserIdAndEntitlementKey(UUID userId, String entitlementKey);
    List<UserEntitlement> findAllByUserIdAndSource(UUID userId, String source);
    List<UserEntitlement> findAllByEntitlementKey(String entitlementKey);
}
