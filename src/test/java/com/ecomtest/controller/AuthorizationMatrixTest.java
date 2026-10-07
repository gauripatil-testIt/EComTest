package com.ecomtest.controller;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationMatrixTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "Password123!";

    private String registerAndLogin(Role role) throws Exception {
        String username = "authz-test-" + role.name().toLowerCase() + "-" + UUID.randomUUID();

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        user.setRole(role);
        userRepository.save(user);

        String loginPayload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, RAW_PASSWORD);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
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
    void unauthenticatedRequestToProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProduct() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String staffToken = registerAndLogin(Role.STAFF);
        Long productId = createProduct(adminToken, "SKU-AUTHZ-STAFF-DEL", "ACTIVE");

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotDeleteOrder() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String customerToken = registerAndLogin(Role.CUSTOMER);
        Long productId = createProduct(adminToken, "SKU-AUTHZ-CUST-DEL", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotViewAnotherCustomersOrder() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String customerOneToken = registerAndLogin(Role.CUSTOMER);
        String customerTwoToken = registerAndLogin(Role.CUSTOMER);
        Long productId = createProduct(adminToken, "SKU-AUTHZ-CUST-VIEW", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerOneToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customerTwoToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customerOneToken))
                .andExpect(status().isOk());
    }

    @Test
    void customerSeesOnlyActiveProducts() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String customerToken = registerAndLogin(Role.CUSTOMER);

        createProduct(adminToken, "SKU-AUTHZ-ACTIVE", "ACTIVE");
        Long inactiveId = createProduct(adminToken, "SKU-AUTHZ-INACTIVE", "INACTIVE");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status != 'ACTIVE')]").isEmpty());

        mockMvc.perform(get("/api/products/{id}", inactiveId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerSeesOnlyOwnOrders() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String customerOneToken = registerAndLogin(Role.CUSTOMER);
        String customerTwoToken = registerAndLogin(Role.CUSTOMER);
        Long productId = createProduct(adminToken, "SKU-AUTHZ-OWN-ORDERS", "ACTIVE");

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerOneToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerTwoToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        String response = mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + customerOneToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var orders = objectMapper.readTree(response);
        for (var order : orders) {
            org.junit.jupiter.api.Assertions.assertFalse(order.get("customerUsername").isNull());
        }
    }

    @Test
    void staffUpdateOnlyChangesStatus() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);
        String staffToken = registerAndLogin(Role.STAFF);
        String customerToken = registerAndLogin(Role.CUSTOMER);
        Long productId = createProduct(adminToken, "SKU-AUTHZ-STAFF-UPDATE", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String updatedPayload = """
                {
                  "customerName": "Someone Else",
                  "productId": %d,
                  "quantity": 999,
                  "unitPrice": 1.23,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.unitPrice").value(19.99));
    }

    @Test
    void noResponseBodyContainsPasswordField() throws Exception {
        String adminToken = registerAndLogin(Role.ADMIN);

        String productResponse = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-AUTHZ-NO-PASSWORD", "ACTIVE")))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(productResponse.contains("password"));

        Long productId = objectMapper.readTree(productResponse).get("id").asLong();

        String getProductResponse = mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(getProductResponse.contains("password"));

        String orderResponse = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(orderResponse.contains("password"));

        String userPayload = """
                {
                  "username": "no-password-user-%s",
                  "password": "Password123!",
                  "role": "CUSTOMER"
                }
                """.formatted(UUID.randomUUID());

        String userResponse = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(userResponse.contains("password"));
    }
}
