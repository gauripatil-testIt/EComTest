package com.ecomtest.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.service.UserService;
import org.junit.jupiter.api.Assertions;
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
class SecurityMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    private String loginPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private String token(String username, Role role) throws Exception {
        String password = "Password123!";
        userService.create(username, password, role);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, password)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
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

    private Long createProduct(String adminToken, String sku, String status) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
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

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotDeleteProducts() throws Exception {
        String adminToken = token("sm-admin-del-cust", Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-SM-DEL-CUST-1", "ACTIVE");

        String customerToken = token("sm-customer-del", Role.CUSTOMER);

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotDeleteProducts() throws Exception {
        String adminToken = token("sm-admin-del-staff", Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-SM-DEL-STAFF-1", "ACTIVE");

        String staffToken = token("sm-staff-del", Role.STAFF);

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCreateOrders() throws Exception {
        String adminToken = token("sm-admin-staff-order", Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-SM-STAFF-ORDER-1", "ACTIVE");

        String staffToken = token("sm-staff-order", Role.STAFF);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffUpdateOnlyChangesStatus() throws Exception {
        String adminToken = token("sm-admin-staff-update", Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-SM-STAFF-UPDATE-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String staffToken = token("sm-staff-update", Role.STAFF);

        String updatePayload = """
                {
                  "customerName": "Someone Else",
                  "productId": %d,
                  "quantity": 99,
                  "unitPrice": 999.99,
                  "status": "SHIPPED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(19.99));
    }

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        String adminToken = token("sm-admin-cust-orders", Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-SM-CUST-ORDERS-1", "ACTIVE");

        String customerAToken = token("sm-customer-a", Role.CUSTOMER);
        String customerBToken = token("sm-customer-b", Role.CUSTOMER);

        String orderAResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderAId = objectMapper.readTree(orderAResponse).get("id").asLong();

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        String listResponse = mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode orders = objectMapper.readTree(listResponse);
        Assertions.assertEquals(1, orders.size());
        Assertions.assertEquals(orderAId, orders.get(0).get("id").asLong());
    }

    @Test
    void customerProductsExcludeInactive() throws Exception {
        String adminToken = token("sm-admin-products", Role.ADMIN);
        createProduct(adminToken, "SKU-SM-ACTIVE-1", "ACTIVE");
        Long inactiveId = createProduct(adminToken, "SKU-SM-INACTIVE-1", "INACTIVE");

        String customerToken = token("sm-customer-products", Role.CUSTOMER);

        String response = mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode products = objectMapper.readTree(response);
        boolean sawInactive = false;
        for (JsonNode product : products) {
            if (product.get("id").asLong() == inactiveId) {
                sawInactive = true;
            }
        }
        Assertions.assertFalse(sawInactive, "Inactive product should not be visible to a CUSTOMER");
    }

    @Test
    void userResponsesNeverContainPassword() throws Exception {
        userService.create("sm-nopassword-user", "Password123!", Role.CUSTOMER);
        String adminToken = token("sm-nopassword-admin", Role.ADMIN);

        String response = mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertFalse(response.contains("password"), "User list response should not contain a password field");
    }
}
