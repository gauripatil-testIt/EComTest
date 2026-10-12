package com.ecomtest.controller;

import com.ecomtest.repository.UserRepository;
import com.ecomtest.support.TestUserSeeder;
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
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private record AuthContext(String header, String username) {
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
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId) {
        return orderPayload("Jane Doe", productId, 2, "19.99", "PENDING");
    }

    private String orderPayload(String customerName, Long productId, int quantity, String unitPrice, String status) {
        return """
                {
                  "customerName": "%s",
                  "productId": %d,
                  "quantity": %d,
                  "unitPrice": %s,
                  "status": "%s"
                }
                """.formatted(customerName, productId, quantity, unitPrice, status);
    }

    private String authHeaderFor(String role) throws Exception {
        return authContextFor(role).header();
    }

    private AuthContext authContextFor(String role) throws Exception {
        TestUserSeeder seeder = new TestUserSeeder(userRepository, passwordEncoder);
        String username = "test-" + role.toLowerCase() + "-" + UUID.randomUUID();
        switch (role) {
            case "ADMIN" -> seeder.seedAdmin(username);
            case "STAFF" -> seeder.seedStaff(username);
            case "CUSTOMER" -> seeder.seedCustomer(username);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        }
        return new AuthContext(login(username), username);
    }

    private String login(String username) throws Exception {
        String loginPayload = """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, TestUserSeeder.DEFAULT_PASSWORD);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();
        return "Bearer " + token;
    }

    @Test
    void createsAndFetchesOrder() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        Long productId = createProduct("SKU-ORD-CREATE-1", adminAuth);

        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"));
    }

    @Test
    void listsOrders() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        Long productId = createProduct("SKU-ORD-LIST-1", adminAuth);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesOrder() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        Long productId = createProduct("SKU-ORD-UPDATE-1", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String updatedPayload = """
                {
                  "customerName": "Jane Smith",
                  "productId": %d,
                  "quantity": 5,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Smith"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesOrder() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        Long productId = createProduct("SKU-ORD-DELETE-1", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderWithUnknownProduct() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(1L)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/orders/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(1L)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/orders/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffUpdateOnlyChangesStatus() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        String staffAuth = authHeaderFor("STAFF");
        Long productId = createProduct("SKU-ORD-STAFF-1", adminAuth);
        Long otherProductId = createProduct("SKU-ORD-STAFF-2", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload("Jane Doe", productId, 2, "19.99", "PENDING")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String staffPayload = orderPayload("Someone Else", otherProductId, 9, "99.99", "CONFIRMED");

        mockMvc.perform(put("/api/orders/{id}", id)
                        .header("Authorization", staffAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.customerName").value("Jane Doe"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.unitPrice").value(19.99));
    }

    @Test
    void customerCannotSeeAnotherCustomersOrder() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        AuthContext alice = authContextFor("CUSTOMER");
        AuthContext bob = authContextFor("CUSTOMER");
        Long productId = createProduct("SKU-ORD-CUSTOMER-1", adminAuth);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", alice.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(alice.username(), productId, 1, "19.99", "PENDING")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value(alice.username()))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", bob.header()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", alice.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value(alice.username()));
    }
}
