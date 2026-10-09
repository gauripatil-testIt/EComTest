package com.ecomtest.specification;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? null
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Product> priceGreaterOrEqual(BigDecimal minPrice) {
        return (root, query, criteriaBuilder) -> minPrice == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessOrEqual(BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> maxPrice == null
                ? null
                : criteriaBuilder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> inStock(Boolean inStock) {
        return (root, query, criteriaBuilder) -> {
            if (inStock == null) {
                return null;
            }
            return inStock
                    ? criteriaBuilder.greaterThan(root.get("stock"), 0)
                    : criteriaBuilder.lessThanOrEqualTo(root.get("stock"), 0);
        };
    }
}
