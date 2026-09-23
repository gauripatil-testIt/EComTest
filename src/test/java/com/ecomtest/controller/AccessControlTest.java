package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccessControlTest {

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
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String adminAuthHeader() throws Exception {
        return "Bearer " + login("admin", "admin123");
    }

    private String staffAuthHeader() throws Exception {
        return "Bearer " + login("staff", "staff123");
    }

    private String customerAuthHeader() throws Exception {
        return "Bearer " + login("customer", "customer123");
    }

    private String productPayload(String sku, String status) {
        return """
                {
                  "name": "Wireless Mouse",
                  "sku": "%s",
                  "price": 19.99,
                  "stock": 100,
                  "status": "%s"
                }
                """.formatted(sku, status);
    }

    private Long createProduct(String sku, String status, String auth) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, status)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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

    private Long createOrder(Long productId, String auth) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void anonymousRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotMutateProducts() throws Exception {
        String admin = adminAuthHeader();
        String customer = customerAuthHeader();

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-ACL-CUST-CREATE-1", "ACTIVE")))
                .andExpect(status().isForbidden());

        Long productId = createProduct("SKU-ACL-CUST-UPDATE-1", "ACTIVE", admin);

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-ACL-CUST-UPDATE-1", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotViewOthersOrder() throws Exception {
        String admin = adminAuthHeader();
        String customer = customerAuthHeader();

        Long productId = createProduct("SKU-ACL-OTHER-ORDER-1", "ACTIVE", admin);
        Long orderId = createOrder(productId, admin);

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerOnlySeesActiveProducts() throws Exception {
        String admin = adminAuthHeader();
        String customer = customerAuthHeader();

        createProduct("SKU-ACL-ACTIVE-1", "ACTIVE", admin);
        createProduct("SKU-ACL-INACTIVE-1", "INACTIVE", admin);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("SKU-ACL-INACTIVE-1"))));
    }

    @Test
    void staffCanUpdateOrderStatusButNotDeleteProduct() throws Exception {
        String admin = adminAuthHeader();
        String staff = staffAuthHeader();

        Long productId = createProduct("SKU-ACL-STAFF-1", "ACTIVE", admin);
        Long orderId = createOrder(productId, admin);

        String statusPayload = """
                {
                  "status": "CONFIRMED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header(HttpHeaders.AUTHORIZATION, staff)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isForbidden());
    }
}
