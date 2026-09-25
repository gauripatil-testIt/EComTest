package com.ecomtest.repository;

import com.ecomtest.dto.ProductSearchProjection;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            SELECT p.id AS id,
                   p.name AS name,
                   p.sku AS sku,
                   p.description AS description,
                   p.price AS price,
                   p.stock AS stock,
                   p.status AS status,
                   (
                     COALESCE(ts_rank(
                                 setweight(to_tsvector('english', coalesce(p.name,'')), 'A')
                                 || setweight(to_tsvector('english', coalesce(p.description,'')), 'B')
                                 || setweight(to_tsvector('english', coalesce(p.sku,'')), 'C'),
                                 plainto_tsquery('english', :q)), 0)
                     + CASE WHEN p.name ILIKE ('%' || :q || '%') THEN 3.0 ELSE 0 END
                     + CASE WHEN p.description ILIKE ('%' || :q || '%') THEN 2.0 ELSE 0 END
                     + CASE WHEN p.sku ILIKE ('%' || :q || '%') THEN 1.0 ELSE 0 END
                   ) AS rank,
                   ts_headline('english', coalesce(p.name,''), plainto_tsquery('english', :q)) AS nameHighlight,
                   ts_headline('english', coalesce(p.sku,''), plainto_tsquery('english', :q)) AS skuHighlight,
                   ts_headline('english', coalesce(p.description,''), plainto_tsquery('english', :q)) AS descriptionHighlight
            FROM products p
            WHERE (
                    p.name ILIKE ('%' || :q || '%')
                    OR p.sku ILIKE ('%' || :q || '%')
                    OR p.description ILIKE ('%' || :q || '%')
                    OR to_tsvector('english', coalesce(p.name,'') || ' ' || coalesce(p.sku,'') || ' ' || coalesce(p.description,'')) @@ plainto_tsquery('english', :q)
                  )
              AND (:#{#status == null ? null : #status.name()} IS NULL OR p.status = :#{#status == null ? null : #status.name()})
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            ORDER BY rank DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM products p
            WHERE (
                    p.name ILIKE ('%' || :q || '%')
                    OR p.sku ILIKE ('%' || :q || '%')
                    OR p.description ILIKE ('%' || :q || '%')
                    OR to_tsvector('english', coalesce(p.name,'') || ' ' || coalesce(p.sku,'') || ' ' || coalesce(p.description,'')) @@ plainto_tsquery('english', :q)
                  )
              AND (:#{#status == null ? null : #status.name()} IS NULL OR p.status = :#{#status == null ? null : #status.name()})
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """,
            nativeQuery = true)
    Page<ProductSearchProjection> searchByRelevance(@Param("q") String q,
                                                     @Param("status") ProductStatus status,
                                                     @Param("minPrice") BigDecimal minPrice,
                                                     @Param("maxPrice") BigDecimal maxPrice,
                                                     Pageable pageable);

    @Query("SELECT p FROM Product p "
            + "WHERE (:status IS NULL OR p.status = :status) "
            + "AND (:minPrice IS NULL OR p.price >= :minPrice) "
            + "AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    Page<Product> findByFilters(@Param("status") ProductStatus status,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 Pageable pageable);
}
