package com.ecomtest.repository;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = "product")
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "product")
    Page<Order> findAll(@NonNull Pageable pageable);
}
