package com.ecomtest.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken() throws Exception {
        return login("admin", "Admin@12345");
    }

    private String login(String username, String password) throws Exception {
        String loginPayload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }

    private String createUserAndLogin(String adminToken, String role) throws Exception {
        String username = role.toLowerCase() + "-" + UUID.randomUUID();
        String password = "Password@12345";
        String userPayload = """
                {
                  "username": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(username, password, role);
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist());
        return login(username, password);
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

    private Long createProduct(String token, String sku, String status) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
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
    void staffCannotCreateOrDeleteProducts() throws Exception {
        String adminToken = adminToken();
        String staffToken = createUserAndLogin(adminToken, "STAFF");
        Long productId = createProduct(adminToken, "SKU-AUTHZ-STAFF-1", "ACTIVE");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-AUTHZ-STAFF-2", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateUpdateOrDeleteProducts() throws Exception {
        String adminToken = adminToken();
        String customerToken = createUserAndLogin(adminToken, "CUSTOMER");
        Long productId = createProduct(adminToken, "SKU-AUTHZ-CUST-1", "ACTIVE");

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-AUTHZ-CUST-2", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-AUTHZ-CUST-1", "ACTIVE")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerProductListExcludesNonActiveProducts() throws Exception {
        String adminToken = adminToken();
        String customerToken = createUserAndLogin(adminToken, "CUSTOMER");

        createProduct(adminToken, "SKU-AUTHZ-ACTIVE-1", "ACTIVE");
        Long inactiveId = createProduct(adminToken, "SKU-AUTHZ-INACTIVE-1", "INACTIVE");

        String response = mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode list = objectMapper.readTree(response);
        for (JsonNode product : list) {
            if (product.get("id").asLong() == inactiveId) {
                throw new AssertionError("Customer product list should not contain non-ACTIVE products");
            }
        }

        mockMvc.perform(get("/api/products/{id}", inactiveId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotSeeAnotherCustomersOrder() throws Exception {
        String adminToken = adminToken();
        String customer1Token = createUserAndLogin(adminToken, "CUSTOMER");
        String customer2Token = createUserAndLogin(adminToken, "CUSTOMER");
        Long productId = createProduct(adminToken, "SKU-AUTHZ-ORD-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customer1Token))
                .andExpect(status().isOk());
    }

    @Test
    void staffCanUpdateOrderStatusButNotFullOrder() throws Exception {
        String adminToken = adminToken();
        String staffToken = createUserAndLogin(adminToken, "STAFF");
        Long productId = createProduct(adminToken, "SKU-AUTHZ-ORD-STATUS-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isForbidden());

        String statusPayload = """
                {
                  "status": "SHIPPED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void customerCannotUpdateOrderStatus() throws Exception {
        String adminToken = adminToken();
        String customerToken = createUserAndLogin(adminToken, "CUSTOMER");
        Long productId = createProduct(adminToken, "SKU-AUTHZ-ORD-STATUS-2", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String statusPayload = """
                {
                  "status": "SHIPPED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void productAndOrderResponsesDoNotExposeAuditFields() throws Exception {
        String adminToken = adminToken();
        Long productId = createProduct(adminToken, "SKU-AUTHZ-AUDIT-1", "ACTIVE");

        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.modifiedBy").doesNotExist());

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.modifiedBy").doesNotExist());
    }
}
