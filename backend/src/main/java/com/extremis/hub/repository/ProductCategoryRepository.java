package com.extremis.hub.repository;

import com.extremis.hub.domain.ProductCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
    Optional<ProductCategory> findBySlug(String slug);
}
