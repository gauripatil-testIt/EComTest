package com.ecomtest.repository;

import com.ecomtest.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    java.util.List<Order> findAllByCreatedBy(com.ecomtest.entity.User user);
}
