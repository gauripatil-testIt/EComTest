package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityMatrixTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        String payload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String authHeader(String username, String password) throws Exception {
        return "Bearer " + login(username, password);
    }

    private String adminAuthHeader() throws Exception {
        return authHeader("admin", "Admin@123");
    }

    private String createUserAndGetAuthHeader(String role) throws Exception {
        String username = "smt-" + role.toLowerCase() + "-" + COUNTER.incrementAndGet();
        String password = "Password@123";
        String payload = """
                {
                  "username": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(username, password, role);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        return authHeader(username, password);
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

    private String orderPayload(Long productId) {
        return """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);
    }

    private Long createProduct(String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void unauthenticatedRequestToProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProducts() throws Exception {
        Long productId = createProduct("SKU-MATRIX-STAFF-DEL-1");
        String staffAuth = createUserAndGetAuthHeader("STAFF");

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", staffAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCreateOrders() throws Exception {
        Long productId = createProduct("SKU-MATRIX-STAFF-ORD-1");
        String staffAuth = createUserAndGetAuthHeader("STAFF");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateProducts() throws Exception {
        String customerAuth = createUserAndGetAuthHeader("CUSTOMER");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", customerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-MATRIX-CUST-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffAndCustomerCannotManageUsers() throws Exception {
        String staffAuth = createUserAndGetAuthHeader("STAFF");
        String customerAuth = createUserAndGetAuthHeader("CUSTOMER");

        mockMvc.perform(get("/api/users")
                        .header("Authorization", staffAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", customerAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestToUsersReturns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noResponseBodyEverContainsPasswordField() throws Exception {
        String adminAuth = adminAuthHeader();

        String username = "smt-nopassword-" + COUNTER.incrementAndGet();
        String createPayload = """
                {
                  "username": "%s",
                  "password": "Password@123",
                  "role": "CUSTOMER"
                }
                """.formatted(username);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(get("/api/users")
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].password").doesNotExist());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "Password@123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
