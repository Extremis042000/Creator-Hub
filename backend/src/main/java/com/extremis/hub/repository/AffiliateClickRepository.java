package com.extremis.hub.repository;

import com.extremis.hub.domain.AffiliateClick;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateClickRepository extends JpaRepository<AffiliateClick, UUID> {
}
