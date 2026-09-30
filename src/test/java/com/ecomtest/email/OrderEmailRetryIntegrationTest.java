package com.ecomtest.email;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the real application context (real {@code emailTaskExecutor}, real
 * {@code @Retryable}/{@code @Recover} AOP) but swaps in a {@link JavaMailSender} test double that
 * always fails to send, so we can observe the retry-with-backoff-then-recover pipeline end to end
 * without needing a real SMTP server, and confirm that the order-update request itself is never
 * blocked by (or dependent on the outcome of) email delivery.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderEmailRetryIntegrationTest {

    @TestConfiguration
    static class FailingMailSenderConfig {
        @Bean
        @Primary
        public FailingJavaMailSender failingJavaMailSender() {
            return new FailingJavaMailSender();
        }
    }

    static class FailingJavaMailSender extends JavaMailSenderImpl {
        private final AtomicInteger sendAttempts = new AtomicInteger();

        @Override
        public void send(MimeMessage mimeMessage) {
            sendAttempts.incrementAndGet();
            throw new RuntimeException("Simulated mail send failure");
        }

        int attemptCount() {
            return sendAttempts.get();
        }

        void reset() {
            sendAttempts.set(0);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FailingJavaMailSender failingJavaMailSender;

    private Logger orderEmailServiceLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        failingJavaMailSender.reset();

        orderEmailServiceLogger = (Logger) LoggerFactory.getLogger(OrderEmailService.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        orderEmailServiceLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        orderEmailServiceLogger.detachAppender(listAppender);
    }

    private String productPayload(String sku) {
        return """
                {
                  "name": "Wireless Mouse",
                  "sku": "%s",
                  "price": 19.99,
                  "stock": 100,
                  "status": "ACTIVE"
                }
                """.formatted(sku);
    }

    private Long createProduct(String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId, String status) {
        return """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "%s",
                  "customerEmail": "jane@example.com"
                }
                """.formatted(productId, status);
    }

    private void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMillis) {
                throw new AssertionError("Condition not met within " + timeoutMillis + "ms");
            }
            Thread.sleep(100);
        }
    }

    @Test
    void orderUpdateSucceedsAndRetriesEmailThreeTimesBeforeRecovering() throws Exception {
        Long productId = createProduct("SKU-RETRY-1");

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        // Creation itself triggers a PENDING email attempt; wait for that to exhaust retries
        // and reset the counter so we cleanly observe only the update-triggered transition below.
        waitUntil(() -> failingJavaMailSender.attemptCount() >= 3, 10_000);
        failingJavaMailSender.reset();
        listAppender.list.clear();

        long start = System.currentTimeMillis();
        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "CONFIRMED")))
                .andExpect(status().isOk());
        long elapsedMillis = System.currentTimeMillis() - start;

        // The order update responds well before the ~3s retry/backoff pipeline for the email
        // completes, proving the API response does not wait on (or depend on) email delivery.
        assertThat(elapsedMillis).isLessThan(2_000);

        waitUntil(() -> failingJavaMailSender.attemptCount() == 3, 10_000);
        assertThat(failingJavaMailSender.attemptCount()).isEqualTo(3);

        waitUntil(() -> listAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains("Giving up sending order status email")),
                10_000);
    }
}
