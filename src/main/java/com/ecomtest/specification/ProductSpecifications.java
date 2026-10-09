package com.ecomtest.specification;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> withFilters(ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (inStock != null) {
                if (inStock) {
                    predicates.add(cb.greaterThan(root.get("stock"), 0));
                } else {
                    predicates.add(cb.lessThanOrEqualTo(root.get("stock"), 0));
                }
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
