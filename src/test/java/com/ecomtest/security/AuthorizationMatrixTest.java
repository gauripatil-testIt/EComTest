package com.ecomtest.security;

import com.ecomtest.entity.User;
import com.ecomtest.entity.UserRole;
import com.ecomtest.repository.UserRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.security.admin.username}")
    private String adminUsername;

    @Value("${app.security.admin.password}")
    private String adminPassword;

    private static final String STAFF_PASSWORD = "staff-pass-123";
    private static final String CUSTOMER_PASSWORD = "customer-pass-123";

    private String adminToken;
    private String staffToken;
    private String customerToken;
    private String otherCustomerToken;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(adminUsername, adminPassword);

        String staffUsername = createUser(UserRole.STAFF, STAFF_PASSWORD);
        staffToken = login(staffUsername, STAFF_PASSWORD);

        String customerUsername = createUser(UserRole.CUSTOMER, CUSTOMER_PASSWORD);
        customerToken = login(customerUsername, CUSTOMER_PASSWORD);

        String otherCustomerUsername = createUser(UserRole.CUSTOMER, CUSTOMER_PASSWORD);
        otherCustomerToken = login(otherCustomerUsername, CUSTOMER_PASSWORD);
    }

    private String createUser(UserRole role, String rawPassword) {
        User user = new User();
        user.setUsername(role.name().toLowerCase() + "-" + UUID.randomUUID());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        return userRepository.save(user).getUsername();
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
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
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
                .andExpect(status().isCreated())
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
    void unauthenticatedRequestToProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProduct() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-DEL-1", "ACTIVE");

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotCreateProduct() throws Exception {
        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-AUTHZ-CREATE-1", "ACTIVE")))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyActiveProducts() throws Exception {
        createProduct("SKU-AUTHZ-ACTIVE-1", "ACTIVE");
        createProduct("SKU-AUTHZ-INACTIVE-1", "INACTIVE");

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status == 'INACTIVE')]").isEmpty());
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-ORD-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Owner Customer", 1)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertTrue(status == 404 || status == 403,
                            "Expected 404 or 403 but got " + status);
                });
    }

    @Test
    void customerCannotSeeOtherCustomersOrdersInList() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-ORD-LIST-1", "ACTIVE");

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId, "Owner Customer", 1)));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + otherCustomerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customerName == 'Owner Customer')]").isEmpty());
    }

    @Test
    void staffStatusOnlyUpdateSucceeds() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-STAFF-OK-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Jane Doe", 2)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String statusOnlyUpdate = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusOnlyUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void staffUpdateChangingNonStatusFieldIsForbiddenAndOrderUnchanged() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-STAFF-FORBID-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Jane Doe", 2)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String fieldChangingUpdate = """
                {
                  "customerName": "Someone Else",
                  "productId": %d,
                  "quantity": 9,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fieldChangingUpdate))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void adminUpdateChangingAnyFieldSucceeds() throws Exception {
        Long productId = createProduct("SKU-AUTHZ-ADMIN-UPDATE-1", "ACTIVE");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Jane Doe", 2)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String fullUpdate = """
                {
                  "customerName": "Jane Smith",
                  "productId": %d,
                  "quantity": 7,
                  "unitPrice": 19.99,
                  "status": "SHIPPED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullUpdate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Smith"))
                .andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }
}
