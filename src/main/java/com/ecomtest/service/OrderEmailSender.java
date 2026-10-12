package com.ecomtest.service;

import com.ecomtest.entity.EmailDeliveryStatus;
import com.ecomtest.entity.EmailNotification;
import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.exception.EmailDeliveryException;
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
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Renders the order status Thymeleaf templates (HTML + plaintext) and sends the
 * resulting multipart email through {@link JavaMailSender}. Transient failures are
 * retried with exponential backoff; once retries are exhausted {@link #recover} marks
 * the corresponding {@link EmailNotification} row as FAILED for manual follow-up
 * instead of rethrowing.
 */
@Service
public class OrderEmailSender {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailSender.class);
    private static final String TEMPLATE_NAME = "order-email";

    private final JavaMailSender mailSender;
    private final ITemplateEngine htmlTemplateEngine;
    private final ITemplateEngine textTemplateEngine;
    private final EmailNotificationRepository emailNotificationRepository;

    public OrderEmailSender(JavaMailSender mailSender,
                             ITemplateEngine htmlTemplateEngine,
                             EmailNotificationRepository emailNotificationRepository) {
        this.mailSender = mailSender;
        this.htmlTemplateEngine = htmlTemplateEngine;
        this.emailNotificationRepository = emailNotificationRepository;
        this.textTemplateEngine = buildTextTemplateEngine();
    }

    private static ITemplateEngine buildTextTemplateEngine() {
        ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
        textResolver.setPrefix("templates/");
        textResolver.setSuffix(".txt");
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setCharacterEncoding("UTF-8");
        textResolver.setCheckExistence(true);
        textResolver.setOrder(1);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(textResolver);
        return engine;
    }

    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void send(Order order, OrderStatus targetStatus) {
        EmailNotification notification = emailNotificationRepository
                .findByOrderIdAndStatus(order.getId(), targetStatus)
                .orElseThrow(() -> new EmailDeliveryException(
                        "No EmailNotification record found for order " + order.getId() + " status " + targetStatus));

        notification.setAttemptCount(notification.getAttemptCount() + 1);
        notification.setUpdatedAt(Instant.now());
        emailNotificationRepository.save(notification);

        Context context = buildContext(order, targetStatus);
        String htmlBody = htmlTemplateEngine.process(TEMPLATE_NAME, context);
        String textBody = textTemplateEngine.process(TEMPLATE_NAME, context);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setTo(resolveRecipient(order));
            helper.setSubject("Order #" + order.getId() + " - " + targetStatus.name());
            helper.setText(textBody, htmlBody);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new EmailDeliveryException(
                    "Failed to send order status email for order " + order.getId() + " status " + targetStatus, e);
        }

        notification.setDeliveryStatus(EmailDeliveryStatus.SENT);
        notification.setUpdatedAt(Instant.now());
        emailNotificationRepository.save(notification);
    }

    @Recover
    public void recover(Exception exception, Order order, OrderStatus targetStatus) {
        log.error("Giving up sending order status email for order {} status {} after retries",
                order.getId(), targetStatus, exception);

        emailNotificationRepository.findByOrderIdAndStatus(order.getId(), targetStatus)
                .ifPresentOrElse(notification -> {
                    notification.setDeliveryStatus(EmailDeliveryStatus.FAILED);
                    notification.setUpdatedAt(Instant.now());
                    emailNotificationRepository.save(notification);
                }, () -> log.warn(
                        "No EmailNotification record found for order {} status {} while recording failure",
                        order.getId(), targetStatus));
    }

    private Context buildContext(Order order, OrderStatus targetStatus) {
        Context context = new Context();
        context.setVariable("orderId", order.getId());
        context.setVariable("customerName", order.getCustomerName());
        context.setVariable("productName", order.getProduct() != null ? order.getProduct().getName() : null);
        context.setVariable("quantity", order.getQuantity());
        BigDecimal total = (order.getUnitPrice() != null && order.getQuantity() != null)
                ? order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()))
                : null;
        context.setVariable("total", total);
        context.setVariable("status", targetStatus.name());
        // No tracking-number field exists on Order today; templates render
        // "tracking not available" whenever this variable is absent/null.
        context.setVariable("trackingNumber", null);
        return context;
    }

    private String resolveRecipient(Order order) {
        // Order/OrderRequest has no dedicated customer email field today, so a
        // deterministic placeholder address is used until one is introduced.
        return "order-" + order.getId() + "@notifications.ecomtest.example";
    }
}
