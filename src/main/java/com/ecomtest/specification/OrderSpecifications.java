package com.ecomtest.specification;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, criteriaBuilder) -> status == null
                ? null
                : criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Order> customerNameContains(String customerName) {
        return (root, query, criteriaBuilder) -> (customerName == null || customerName.isBlank())
                ? null
                : criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("customerName")),
                        "%" + customerName.toLowerCase() + "%");
    }

    public static Specification<Order> hasProductId(Long productId) {
        return (root, query, criteriaBuilder) -> productId == null
                ? null
                : criteriaBuilder.equal(root.get("product").get("id"), productId);
    }

    public static Specification<Order> createdAfter(Instant dateFrom) {
        return (root, query, criteriaBuilder) -> dateFrom == null
                ? null
                : criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), dateFrom);
    }

    public static Specification<Order> createdBefore(Instant dateTo) {
        return (root, query, criteriaBuilder) -> dateTo == null
                ? null
                : criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), dateTo);
    }
}
