package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderEmail;
import com.ecomtest.entity.OrderEmailState;
import com.ecomtest.event.OrderEmailRequested;
import com.ecomtest.repository.OrderEmailRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class OrderEmailService {

    private static final Logger log = LoggerFactory.getLogger(OrderEmailService.class);
    private static final int MAX_ATTEMPTS = 3;

    private final JavaMailSender mailSender;
    private final OrderEmailRepository orderEmailRepository;
    private final SpringTemplateEngine templateEngine;
    private final String fromAddress;
    private final long backoffMillis;

    public OrderEmailService(JavaMailSender mailSender, OrderEmailRepository orderEmailRepository,
            @Value("${app.email.from}") String fromAddress,
            @Value("${app.email.retry.backoff-ms:1000}") long backoffMillis) {
        this.mailSender = mailSender;
        this.orderEmailRepository = orderEmailRepository;
        this.fromAddress = fromAddress;
        this.backoffMillis = backoffMillis;
        this.templateEngine = buildTemplateEngine();
    }

    private SpringTemplateEngine buildTemplateEngine() {
        ClassLoaderTemplateResolver htmlResolver = new ClassLoaderTemplateResolver();
        htmlResolver.setPrefix("templates/");
        htmlResolver.setSuffix("");
        htmlResolver.setTemplateMode(TemplateMode.HTML);
        htmlResolver.setCharacterEncoding("UTF-8");
        htmlResolver.setResolvablePatterns(Set.of("email/*.html"));
        htmlResolver.setOrder(1);
        htmlResolver.setCacheable(false);

        ClassLoaderTemplateResolver textResolver = new ClassLoaderTemplateResolver();
        textResolver.setPrefix("templates/");
        textResolver.setSuffix("");
        textResolver.setTemplateMode(TemplateMode.TEXT);
        textResolver.setCharacterEncoding("UTF-8");
        textResolver.setResolvablePatterns(Set.of("email/*.txt"));
        textResolver.setOrder(2);
        textResolver.setCacheable(false);

        SpringTemplateEngine engine = new SpringTemplateEngine();
        Set<ITemplateResolver> resolvers = new LinkedHashSet<>();
        resolvers.add(htmlResolver);
        resolvers.add(textResolver);
        engine.setTemplateResolvers(resolvers);
        return engine;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderEmailRequested event) {
        Optional<OrderEmail> maybeOrderEmail = orderEmailRepository.findById(event.orderEmailId());
        if (maybeOrderEmail.isEmpty()) {
            log.warn("OrderEmail {} not found; skipping send", event.orderEmailId());
            return;
        }
        OrderEmail orderEmail = maybeOrderEmail.get();
        if (orderEmail.getState() == OrderEmailState.SENT) {
            return;
        }
        sendWithRetry(orderEmail);
    }

    void sendWithRetry(OrderEmail orderEmail) {
        Order order = orderEmail.getOrder();
        Context context = buildContext(order);

        String subject = templateEngine.process("email/order-status-subject.txt", context).strip();
        String htmlBody = templateEngine.process("email/order-status.html", context);
        String textBody = templateEngine.process("email/order-status.txt", context);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            orderEmail.setAttempts(attempt);
            try {
                sendMessage(order.getCustomerEmail(), subject, textBody, htmlBody);
                orderEmail.setState(OrderEmailState.SENT);
                orderEmail.setLastError(null);
                orderEmailRepository.save(orderEmail);
                return;
            } catch (Exception ex) {
                lastException = ex;
                orderEmail.setLastError(ex.getMessage());
                orderEmailRepository.save(orderEmail);
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        orderEmail.setState(OrderEmailState.FAILED);
        orderEmailRepository.save(orderEmail);
        log.error("Failed to send order status email for order {} status {} after {} attempts",
                order.getId(), orderEmail.getStatus(), MAX_ATTEMPTS, lastException);
    }

    private void backoff(int attempt) {
        try {
            long delay = backoffMillis * (1L << (attempt - 1));
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendMessage(String to, String subject, String textBody, String htmlBody) throws Exception {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(to);
        helper.setFrom(fromAddress);
        helper.setSubject(subject);
        helper.setText(textBody, htmlBody);
        mailSender.send(mimeMessage);
    }

    private Context buildContext(Order order) {
        Context context = new Context();
        context.setVariable("orderId", order.getId());
        context.setVariable("customerName", order.getCustomerName());
        context.setVariable("productName", order.getProduct() != null ? order.getProduct().getName() : null);
        context.setVariable("quantity", order.getQuantity());
        context.setVariable("unitPrice", order.getUnitPrice());
        BigDecimal total = (order.getUnitPrice() != null && order.getQuantity() != null)
                ? order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()))
                : null;
        context.setVariable("total", total);
        context.setVariable("status", order.getStatus() != null ? order.getStatus().name() : null);
        context.setVariable("trackingNumber", order.getTrackingNumber());
        context.setVariable("trackingUrl", order.getTrackingUrl());
        return context;
    }
}
