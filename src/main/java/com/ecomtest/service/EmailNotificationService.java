package com.ecomtest.service;

import com.ecomtest.entity.EmailNotification;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.repository.EmailNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailNotificationRepository emailNotificationRepository;
    private final OrderEmailSender orderEmailSender;

    public EmailNotificationService(EmailNotificationRepository emailNotificationRepository,
                                     OrderEmailSender orderEmailSender) {
        this.emailNotificationRepository = emailNotificationRepository;
        this.orderEmailSender = orderEmailSender;
    }

    @Async
    public void notifyStatusChange(Order order, OrderStatus status) {
        if (emailNotificationRepository.findByOrderIdAndStatus(order.getId(), status).isPresent()) {
            log.debug("Email already sent/claimed for order {} status {}, skipping", order.getId(), status);
            return;
        }

        EmailNotification notification = new EmailNotification();
        notification.setOrderId(order.getId());
        notification.setStatus(status);

        try {
            notification = emailNotificationRepository.save(notification);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Email notification for order {} status {} already claimed concurrently, skipping",
                    order.getId(), status);
            return;
        }

        orderEmailSender.send(order, status, notification);
    }
}
