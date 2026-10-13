package com.ecomtest.service;

import tools.jackson.databind.ObjectMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.email.retry.backoff-ms=10")
class OrderEmailNotificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void stubMimeMessageCreation() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getDefaultInstance(new Properties())));
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

    private String orderPayload(Long productId, String customerEmail, String status) {
        String emailField = customerEmail != null
                ? "\"customerEmail\": \"%s\",".formatted(customerEmail)
                : "";
        return """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  %s
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "%s"
                }
                """.formatted(productId, emailField, status);
    }

    private Long createOrder(Long productId, String customerEmail, String status) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, customerEmail, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void updateOrder(Long id, Long productId, String customerEmail, String status) throws Exception {
        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, customerEmail, status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private boolean subjectContains(MimeMessage message, String fragment) {
        try {
            String subject = message.getSubject();
            return subject != null && subject.contains(fragment);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sendsOneEmailWithPendingSubjectOnCreate() throws Exception {
        Long productId = createProduct("SKU-NOTIF-PENDING-1");
        createOrder(productId, "jane@example.com", "PENDING");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(1)).send(captor.capture());

        assertTrue(subjectContains(captor.getValue(), "Order Confirmation"));
    }

    @Test
    void puttingSameStatusTwiceSendsOnlyOneEmailTotal() throws Exception {
        Long productId = createProduct("SKU-NOTIF-SAMESTATUS-1");
        Long id = createOrder(productId, "jane@example.com", "PENDING");

        updateOrder(id, productId, "jane@example.com", "PENDING");
        updateOrder(id, productId, "jane@example.com", "PENDING");

        verify(mailSender, timeout(3000).times(1)).send(any(MimeMessage.class));
    }

    @Test
    void movingShippedThenCancelledTwiceSendsExactlyOneCancelledEmail() throws Exception {
        Long productId = createProduct("SKU-NOTIF-CANCEL-1");
        Long id = createOrder(productId, "jane@example.com", "SHIPPED");

        updateOrder(id, productId, "jane@example.com", "CANCELLED");
        updateOrder(id, productId, "jane@example.com", "CANCELLED");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, timeout(5000).times(2)).send(captor.capture());

        long cancelledCount = captor.getAllValues().stream()
                .filter(message -> subjectContains(message, "Cancelled"))
                .count();
        assertEquals(1, cancelledCount);
    }

    @Test
    void updatingOrderWithoutCustomerEmailSendsNoMail() throws Exception {
        Long productId = createProduct("SKU-NOTIF-NOEMAIL-1");
        Long id = createOrder(productId, null, "PENDING");

        updateOrder(id, productId, null, "CONFIRMED");

        verify(mailSender, timeout(1000).times(0)).send(any(MimeMessage.class));
    }
}
