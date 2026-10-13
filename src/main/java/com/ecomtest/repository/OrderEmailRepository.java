package com.ecomtest.repository;

import com.ecomtest.entity.OrderEmail;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderEmailRepository extends JpaRepository<OrderEmail, Long> {

    Optional<OrderEmail> findByOrderIdAndStatus(Long orderId, OrderStatus status);
}
