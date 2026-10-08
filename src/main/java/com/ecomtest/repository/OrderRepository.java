package com.ecomtest.repository;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByCustomer(User customer);
}
