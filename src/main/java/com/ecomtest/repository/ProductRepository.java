package com.ecomtest.repository;

import com.ecomtest.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            SELECT p.* FROM products p
            WHERE to_tsvector('english', coalesce(p.name, '') || ' ' || coalesce(p.sku, '') || ' ' || coalesce(p.description, ''))
                  @@ to_tsquery('english', :tsQuery)
              AND (:status IS NULL OR p.status = :status)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            ORDER BY ts_rank(
                to_tsvector('english', coalesce(p.name, '') || ' ' || coalesce(p.sku, '') || ' ' || coalesce(p.description, '')),
                to_tsquery('english', :tsQuery)
            ) DESC
            """,
            countQuery = """
            SELECT count(*) FROM products p
            WHERE to_tsvector('english', coalesce(p.name, '') || ' ' || coalesce(p.sku, '') || ' ' || coalesce(p.description, ''))
                  @@ to_tsquery('english', :tsQuery)
              AND (:status IS NULL OR p.status = :status)
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """,
            nativeQuery = true)
    Page<Product> searchByRelevance(@Param("tsQuery") String tsQuery,
                                     @Param("status") String status,
                                     @Param("minPrice") BigDecimal minPrice,
                                     @Param("maxPrice") BigDecimal maxPrice,
                                     Pageable pageable);
}
