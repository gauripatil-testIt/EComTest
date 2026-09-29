package com.ecomtest.controller;

import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.entity.Role;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String createUser(String adminToken, String username, String password, Role role) throws Exception {
        String response = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"role\":\"" + role.name() + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    @Test
    void anonymousRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotDeleteOrUpdateProduct() throws Exception {
        String adminToken = login("admin", "admin-password");

        String customerUsername = "cust-1";
        String customerPassword = "cust-password";
        createUser(adminToken, customerUsername, customerPassword, Role.CUSTOMER);
        String customerToken = login(customerUsername, customerPassword);

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wireless Mouse\",\"sku\":\"SKU-SEC-CUST-1\",\"price\":19.99,\"stock\":100,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", auth(customerToken)))
                .andExpect(status().isForbidden());

        String updatedPayload = "{\"name\":\"Wireless Mouse Pro\",\"sku\":\"SKU-SEC-CUST-1\",\"price\":24.99,\"stock\":50,\"status\":\"ACTIVE\"}";
        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", auth(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotSeeOtherCustomersOrders() throws Exception {
        String adminToken = login("admin", "admin-password");

        String productCreated = mockMvc.perform(post("/api/products")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wireless Mouse\",\"sku\":\"SKU-SEC-ORD-1\",\"price\":19.99,\"stock\":100,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(productCreated).get("id").asLong();

        createUser(adminToken, "cust-a", "cust-password", Role.CUSTOMER);
        String custAToken = login("cust-a", "cust-password");

        createUser(adminToken, "cust-b", "cust-password", Role.CUSTOMER);
        String custBToken = login("cust-b", "cust-password");

        String orderA = mockMvc.perform(post("/api/orders")
                        .header("Authorization", auth(custAToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Jane A\",\"productId\":\"" + productId + "\",\"quantity\":2,\"unitPrice\":19.99,\"status\":\"" + OrderStatus.PENDING.name() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderIdA = objectMapper.readTree(orderA).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderIdA)
                        .header("Authorization", auth(custBToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", auth(custBToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void staffUpdateOnlyChangesStatus() throws Exception {
        String adminToken = login("admin", "admin-password");

        String productCreated = mockMvc.perform(post("/api/products")
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Wireless Mouse\",\"sku\":\"SKU-SEC-STF-1\",\"price\":19.99,\"stock\":100,\"status\":\"" + ProductStatus.ACTIVE.name() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(productCreated).get("id").asLong();

        createUser(adminToken, "stf-1", "stf-password", Role.STAFF);
        String staffToken = login("stf-1", "stf-password");

        createUser(adminToken, "cust-1", "cust-password", Role.CUSTOMER);
        String customerToken = login("cust-1", "cust-password");

        String orderCreated = mockMvc.perform(post("/api/orders")
                        .header("Authorization", auth(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerName\":\"Jane Original\",\"productId\":\"" + productId + "\",\"quantity\":2,\"unitPrice\":19.99,\"status\":\"" + OrderStatus.PENDING.name() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(orderCreated).get("id").asLong();

        String updatedPayload = "{\"customerName\":\"Jane Changed\",\"productId\":\"" + productId + "\",\"quantity\":10,\"unitPrice\":29.99,\"status\":\"" + OrderStatus.CONFIRMED.name() + "\"}";
        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", auth(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(OrderStatus.CONFIRMED.name()));

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", auth(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Original"));
    }
}
