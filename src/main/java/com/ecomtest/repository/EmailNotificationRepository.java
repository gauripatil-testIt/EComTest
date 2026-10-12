package com.ecomtest.repository;

import com.ecomtest.entity.EmailNotification;
import com.ecomtest.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailNotificationRepository extends JpaRepository<EmailNotification, Long> {

    Optional<EmailNotification> findByOrderIdAndStatus(Long orderId, OrderStatus status);
}
