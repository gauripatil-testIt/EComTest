package com.ecomtest.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    private String staffToken() throws Exception {
        return login("staff", "staff123");
    }

    private String customerToken() throws Exception {
        return login("customer", "customer123");
    }

    private String customer2Token() throws Exception {
        return login("customer2", "customer123");
    }

    private String bearer(String token) {
        return "Bearer " + token;
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

    private Long createProduct(String sku, String status, String token) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId) {
        return """
                {
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);
    }

    private Long createOrder(Long productId, String token) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private List<Long> readIds(String response) throws Exception {
        JsonNode array = objectMapper.readTree(response);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            ids.add(array.get(i).get("id").asLong());
        }
        return ids;
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotCreateProductReturns403() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", bearer(staffToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-SEC-STAFF-CREATE-1", "ACTIVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotDeleteProductReturns403() throws Exception {
        Long productId = createProduct("SKU-SEC-STAFF-DELETE-1", "ACTIVE", adminToken());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", bearer(staffToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotUpdateOrderReturns403() throws Exception {
        Long productId = createProduct("SKU-SEC-CUST-UPDATE-1", "ACTIVE", adminToken());
        Long orderId = createOrder(productId, customerToken());

        String updatedPayload = """
                {
                  "productId": %d,
                  "quantity": 5,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", bearer(customerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        Long productId = createProduct("SKU-SEC-OWN-ORDERS-1", "ACTIVE", adminToken());

        Long ownOrderId = createOrder(productId, customerToken());
        Long otherOrderId = createOrder(productId, customer2Token());

        String response = mockMvc.perform(get("/api/orders")
                        .header("Authorization", bearer(customerToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> ids = readIds(response);
        assertThat(ids).contains(ownOrderId);
        assertThat(ids).doesNotContain(otherOrderId);
    }

    @Test
    void customerCannotGetOtherCustomersOrderReturns403() throws Exception {
        Long productId = createProduct("SKU-SEC-OTHER-ORDER-1", "ACTIVE", adminToken());
        Long otherOrderId = createOrder(productId, customer2Token());

        mockMvc.perform(get("/api/orders/{id}", otherOrderId)
                        .header("Authorization", bearer(customerToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyActiveProducts() throws Exception {
        String admin = adminToken();
        Long activeId = createProduct("SKU-SEC-ACTIVE-1", "ACTIVE", admin);
        Long inactiveId = createProduct("SKU-SEC-INACTIVE-1", "INACTIVE", admin);

        String response = mockMvc.perform(get("/api/products")
                        .header("Authorization", bearer(customerToken())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> ids = readIds(response);
        assertThat(ids).contains(activeId);
        assertThat(ids).doesNotContain(inactiveId);
    }

    @Test
    void customerCannotGetInactiveProductReturns404() throws Exception {
        Long inactiveId = createProduct("SKU-SEC-INACTIVE-GET-1", "INACTIVE", adminToken());

        mockMvc.perform(get("/api/products/{id}", inactiveId)
                        .header("Authorization", bearer(customerToken())))
                .andExpect(status().isNotFound());
    }

    @Test
    void staffCanUpdateOrderStatus() throws Exception {
        Long productId = createProduct("SKU-SEC-STAFF-STATUS-1", "ACTIVE", adminToken());
        Long orderId = createOrder(productId, customerToken());

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", bearer(staffToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONFIRMED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", bearer(staffToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
