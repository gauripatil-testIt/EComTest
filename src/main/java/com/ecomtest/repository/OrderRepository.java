package com.ecomtest.repository;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.stream.Stream;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatusOrderByIdAsc(OrderStatus status);

    List<Order> findAllByOrderByIdAsc();

    @Query("select o from Order o join fetch o.product where (:status is null or o.status = :status) order by o.id asc")
    Stream<Order> streamForExport(@Param("status") OrderStatus status);
}
