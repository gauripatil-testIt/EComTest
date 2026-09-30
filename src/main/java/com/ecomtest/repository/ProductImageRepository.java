package com.ecomtest.repository;

import com.ecomtest.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(Long productId);

    Optional<ProductImage> findFirstByProductIdOrderBySortOrderAsc(Long productId);

    boolean existsByProductIdAndIsMainTrue(Long productId);
}
