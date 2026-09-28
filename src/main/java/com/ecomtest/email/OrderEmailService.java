package com.ecomtest.email;

import com.ecomtest.entity.OrderStatus;
import com.ecomtest.event.OrderStatusChangedEvent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders and sends the transactional email for an order status transition.
 * <p>
 * Sending is asynchronous (so the order update request is never blocked), retried a bounded
 * number of times with backoff, and deduplicated in-memory per order/status transition so that
 * republishing the same {@link OrderStatusChangedEvent} (e.g. an event redelivery) does not result
 * in a second email being sent once the first attempt has already succeeded.
 */
@Service
public class OrderEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailService.class);

    private final JavaMailSender mailSender;
    private final Configuration freemarkerConfiguration;

    /**
     * In-memory dedup of transitions that have already been successfully sent, keyed by
     * "{orderId}:{status}". Deliberately not persisted, per product decision to rely on
     * in-memory deduplication only.
     */
    private final ConcurrentHashMap<String, Boolean> sentTransitions = new ConcurrentHashMap<>();

    public OrderEmailService(JavaMailSender mailSender,
                              @Qualifier("emailFreemarkerConfiguration") Configuration freemarkerConfiguration) {
        this.mailSender = mailSender;
        this.freemarkerConfiguration = freemarkerConfiguration;
    }

    @Async("emailTaskExecutor")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void sendStatusEmail(OrderStatusChangedEvent event) {
        String transitionKey = transitionKey(event);
        if (sentTransitions.putIfAbsent(transitionKey, Boolean.TRUE) != null) {
            log.debug("Skipping duplicate order status email for order {} transition to {}",
                    event.orderId(), event.newStatus());
            return;
        }

        if (event.customerEmail() == null || event.customerEmail().isBlank()) {
            log.warn("No customer email available for order {}; skipping notification for status {}",
                    event.orderId(), event.newStatus());
            return;
        }

        try {
            String templateBaseName = templateBaseNameFor(event.newStatus());
            Map<String, Object> model = buildModel(event);

            String htmlContent = renderTemplate(templateBaseName + ".html.ftl", model);
            String textContent = renderTemplate(templateBaseName + ".txt.ftl", model);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(event.customerEmail());
            helper.setSubject("Order #" + event.orderId() + " - " + event.newStatus());
            helper.setText(textContent, htmlContent);

            mailSender.send(message);
        } catch (Exception e) {
            // Allow the transition to be attempted again on retry (or on a later redelivery,
            // if retries are exhausted below).
            sentTransitions.remove(transitionKey);
            throw new IllegalStateException("Failed to send order status email for order "
                    + event.orderId() + " status " + event.newStatus(), e);
        }
    }

    @Recover
    public void recoverSendFailure(Exception e, OrderStatusChangedEvent event) {
        log.error("Giving up sending order status email for order {} transition to {} after retries: {}",
                event.orderId(), event.newStatus(), e.getMessage(), e);
    }

    private String transitionKey(OrderStatusChangedEvent event) {
        return event.orderId() + ":" + event.newStatus();
    }

    private Map<String, Object> buildModel(OrderStatusChangedEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("orderId", event.orderId());
        model.put("customerName", event.customerName());
        model.put("productName", event.productName());
        model.put("quantity", event.quantity());
        model.put("unitPrice", event.unitPrice());

        BigDecimal total = (event.unitPrice() != null && event.quantity() != null)
                ? event.unitPrice().multiply(BigDecimal.valueOf(event.quantity()))
                : null;
        model.put("total", total);

        if (event.trackingNumber() != null) {
            model.put("trackingNumber", event.trackingNumber());
        }
        return model;
    }

    private String renderTemplate(String templateName, Map<String, Object> model)
            throws IOException, TemplateException {
        Template template = freemarkerConfiguration.getTemplate(templateName);
        StringWriter writer = new StringWriter();
        template.process(model, writer);
        return writer.toString();
    }

    private String templateBaseNameFor(OrderStatus status) {
        return switch (status) {
            case PENDING -> "order-pending";
            case CONFIRMED -> "order-confirmed";
            case SHIPPED -> "order-shipped";
            case DELIVERED -> "order-delivered";
            case CANCELLED -> "order-cancelled";
        };
    }
}
