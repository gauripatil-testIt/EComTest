package com.ecomtest.service;

import com.ecomtest.entity.EmailDeliveryStatus;
import com.ecomtest.entity.EmailNotification;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.repository.EmailNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Dispatches order status notification emails off the request thread. Guarantees at
 * most one email is sent per (orderId, status) pair by persisting a PENDING
 * {@link EmailNotification} row before attempting to send, and skipping the send
 * entirely when a row for that pair already exists.
 */
@Service
public class OrderEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailDispatcher.class);

    private final EmailNotificationRepository emailNotificationRepository;
    private final OrderEmailSender orderEmailSender;

    public OrderEmailDispatcher(EmailNotificationRepository emailNotificationRepository,
                                 OrderEmailSender orderEmailSender) {
        this.emailNotificationRepository = emailNotificationRepository;
        this.orderEmailSender = orderEmailSender;
    }

    @Async
    public void dispatch(Order order, OrderStatus targetStatus) {
        if (emailNotificationRepository.findByOrderIdAndStatus(order.getId(), targetStatus).isPresent()) {
            log.debug("Email already recorded for order {} status {}, skipping duplicate send",
                    order.getId(), targetStatus);
            return;
        }

        EmailNotification notification = new EmailNotification();
        notification.setOrderId(order.getId());
        notification.setStatus(targetStatus);
        notification.setDeliveryStatus(EmailDeliveryStatus.PENDING);
        notification.setAttemptCount(0);
        notification.setCreatedAt(Instant.now());
        notification.setUpdatedAt(Instant.now());

        try {
            emailNotificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            // A concurrent dispatch already created the row for this (orderId, status)
            // pair; the unique constraint protects us from sending twice.
            log.debug("Concurrent email notification detected for order {} status {}, skipping duplicate send",
                    order.getId(), targetStatus);
            return;
        }

        orderEmailSender.send(order, targetStatus);
    }
}
