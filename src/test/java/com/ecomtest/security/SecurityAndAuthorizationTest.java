package com.ecomtest.security;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class SecurityAndAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login("admin", "change-this-admin-password");
    }

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

    private String createUserAndLogin(String role) throws Exception {
        String username = "user-" + UUID.randomUUID();
        String password = "password123";
        String createPayload = """
                {
                  "username": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(username, password, role);

        String response = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(response.toLowerCase().contains("password"));

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

    private Long createProduct(String sku, String status) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, status)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId, String customerName, int quantity) {
        return """
                {
                  "customerName": "%s",
                  "productId": %d,
                  "quantity": %d,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(customerName, productId, quantity);
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProduct() throws Exception {
        String staffToken = createUserAndLogin("STAFF");
        Long productId = createProduct("SKU-SEC-STAFF-DEL-1", "ACTIVE");

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void customerSeesOnlyActiveProducts() throws Exception {
        String customerToken = createUserAndLogin("CUSTOMER");

        createProduct("SKU-SEC-CUST-ACTIVE-1", "ACTIVE");
        createProduct("SKU-SEC-CUST-INACTIVE-1", "INACTIVE");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("ACTIVE"))));
    }

    @Test
    void customerCannotViewOthersOrder() throws Exception {
        String customer1Token = createUserAndLogin("CUSTOMER");
        String customer2Token = createUserAndLogin("CUSTOMER");

        Long productId = createProduct("SKU-SEC-CUST-ORDER-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Customer One", 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customer2Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffUpdateOnlyChangesStatus() throws Exception {
        String staffToken = createUserAndLogin("STAFF");
        Long productId = createProduct("SKU-SEC-STAFF-UPD-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Original Name", 2)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String updatePayload = """
                {
                  "customerName": "Changed Name",
                  "productId": %d,
                  "quantity": 9,
                  "unitPrice": 999.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Original Name"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }
}
