package com.ecomtest.service;

import com.ecomtest.entity.EmailDeliveryStatus;
import com.ecomtest.entity.EmailNotification;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.repository.EmailNotificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;

@Service
public class OrderEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailSender.class);

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine emailTemplateEngine;
    private final EmailNotificationRepository emailNotificationRepository;

    public OrderEmailSender(JavaMailSender mailSender,
                             SpringTemplateEngine emailTemplateEngine,
                             EmailNotificationRepository emailNotificationRepository) {
        this.mailSender = mailSender;
        this.emailTemplateEngine = emailTemplateEngine;
        this.emailNotificationRepository = emailNotificationRepository;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void send(Order order, OrderStatus status, EmailNotification notification) {
        notification.setAttempts(notification.getAttempts() + 1);
        emailNotificationRepository.save(notification);

        try {
            String templateName = templateNameFor(status);
            Context context = buildContext(order);

            String htmlBody = emailTemplateEngine.process(templateName + ".html", context);
            String textBody = emailTemplateEngine.process(templateName + ".txt", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(order.getCustomerEmail());
            helper.setSubject(subjectFor(status, order));
            helper.setText(textBody, htmlBody);

            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new EmailDeliveryException(
                    "Failed to build/send order status email for order " + order.getId() + " status " + status, ex);
        }

        notification.setDeliveryStatus(EmailDeliveryStatus.SENT);
        emailNotificationRepository.save(notification);
    }

    @Recover
    public void recover(Exception ex, Order order, OrderStatus status, EmailNotification notification) {
        log.error("Failed to send order status email for order {} status {} after retries",
                order.getId(), status, ex);
        notification.setDeliveryStatus(EmailDeliveryStatus.FAILED);
        emailNotificationRepository.save(notification);
    }

    private String templateNameFor(OrderStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case CONFIRMED -> "confirmed";
            case SHIPPED -> "shipped";
            case DELIVERED -> "delivered";
            case CANCELLED -> "cancelled";
        };
    }

    private String subjectFor(OrderStatus status, Order order) {
        return switch (status) {
            case PENDING -> "Order #" + order.getId() + " received";
            case CONFIRMED -> "Order #" + order.getId() + " confirmed";
            case SHIPPED -> "Order #" + order.getId() + " shipped";
            case DELIVERED -> "Order #" + order.getId() + " delivered";
            case CANCELLED -> "Order #" + order.getId() + " cancelled";
        };
    }

    private Context buildContext(Order order) {
        Context context = new Context();
        context.setVariable("orderId", order.getId());
        context.setVariable("customerName", order.getCustomerName());
        context.setVariable("productName", order.getProduct().getName());
        context.setVariable("quantity", order.getQuantity());
        context.setVariable("unitPrice", order.getUnitPrice());
        BigDecimal total = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
        context.setVariable("total", total);
        context.setVariable("trackingNumber", order.getTrackingNumber());
        context.setVariable("carrier", order.getCarrier());
        return context;
    }
}
