package com.extremis.hub.repository;

import com.extremis.hub.domain.AffiliateProduct;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffiliateProductRepository extends JpaRepository<AffiliateProduct, UUID> {
}
