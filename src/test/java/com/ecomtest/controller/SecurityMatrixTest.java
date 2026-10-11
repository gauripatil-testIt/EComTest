package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "Passw0rd!";

    @BeforeEach
    void setUp() {
        ensureUser("matrix-admin", Role.ADMIN);
        ensureUser("matrix-staff", Role.STAFF);
        ensureUser("matrix-customer-a", Role.CUSTOMER);
        ensureUser("matrix-customer-b", Role.CUSTOMER);
    }

    private void ensureUser(String username, Role role) {
        userRepository.findByUsername(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
            user.setRole(role);
            return userRepository.save(user);
        });
    }

    private String authHeader(String username) throws Exception {
        String loginPayload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, RAW_PASSWORD);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        return "Bearer " + token;
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

    private Long createProduct(String sku, String adminAuth) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
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
    void getProductsWithoutAuthorizationReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteProductAsStaffReturns403() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        Long productId = createProduct("SKU-MATRIX-DEL-1", adminAuth);

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, authHeader("matrix-staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createUpdateDeleteProductAsCustomerReturns403() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        String customerAuth = authHeader("matrix-customer-a");
        Long productId = createProduct("SKU-MATRIX-CUST-1", adminAuth);

        mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, customerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-MATRIX-CUST-2")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-MATRIX-CUST-1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotSeeAnotherCustomersOrder() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        String customerAAuth = authHeader("matrix-customer-a");
        String customerBAuth = authHeader("matrix-customer-b");
        Long productId = createProduct("SKU-MATRIX-ORD-1", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerAAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, customerBAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, customerBAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + orderId + ")]").isEmpty());
    }

    @Test
    void staffCanUpdateOnlyOrderStatus() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        String staffAuth = authHeader("matrix-staff");
        Long productId = createProduct("SKU-MATRIX-ORD-2", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        String statusOnlyPayload = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusOnlyPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        String otherFieldsPayload = """
                {
                  "customerName": "Someone Else",
                  "productId": %d,
                  "quantity": 9,
                  "unitPrice": 19.99,
                  "status": "SHIPPED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherFieldsPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOrderAsStaffOrCustomerReturns403AndAsAdminSucceeds() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        String staffAuth = authHeader("matrix-staff");
        String customerAuth = authHeader("matrix-customer-a");
        Long productId = createProduct("SKU-MATRIX-ORD-3", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, staffAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, customerAuth))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isNoContent());
    }

    @Test
    void createOrderAsStaffReturns403() throws Exception {
        String adminAuth = authHeader("matrix-admin");
        String staffAuth = authHeader("matrix-staff");
        Long productId = createProduct("SKU-MATRIX-ORD-4", adminAuth);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsReturn401NotForbidden() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(1L)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userListAndCreateResponsesNeverContainPasswordValue() throws Exception {
        String adminAuth = authHeader("matrix-admin");

        String userPayload = """
                {
                  "username": "matrix-provisioned-user",
                  "password": "%s",
                  "role": "CUSTOMER"
                }
                """.formatted(RAW_PASSWORD);

        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userPayload))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(RAW_PASSWORD))));

        mockMvc.perform(get("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, adminAuth))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(RAW_PASSWORD))));
    }
}
