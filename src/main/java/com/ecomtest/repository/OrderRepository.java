package com.ecomtest.repository;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.stream.Stream;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    @Query("select o from Order o join fetch o.product where o.status = :status")
    Stream<Order> streamForExport(OrderStatus status);
}
