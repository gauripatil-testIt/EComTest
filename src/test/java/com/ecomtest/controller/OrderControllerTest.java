package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.repository.UserRepository;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private String createUser(Role role) {
        String username = "oc-" + role.name().toLowerCase() + "-" + COUNTER.incrementAndGet();
        if (userRepository.findByUsername(username).isEmpty()) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode("password"));
            user.setRole(role);
            user.setEnabled(true);
            userRepository.save(user);
        }
        return username;
    }

    private String token(String username) throws Exception {
        String loginPayload = """
                {
                  "username": "%s",
                  "password": "password"
                }
                """.formatted(username);

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String tokenFor(Role role) throws Exception {
        return token(createUser(role));
    }

    private Long createProduct(String adminToken, String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
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
    void createsAndFetchesOrder() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-CREATE-1");

        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"));
    }

    @Test
    void listsOrders() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-LIST-1");

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesOrder() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-UPDATE-1");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
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
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Smith"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesOrder() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-DELETE-1");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderWithUnknownProduct() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedListRequest() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotCreateOrderButCanReadAndPatchStatus() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String staffToken = tokenFor(Role.STAFF);
        Long productId = createProduct(adminToken, "SKU-ORD-STAFF-1");

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String statusPayload = """
                {
                  "status": "SHIPPED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", id)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        String fullUpdatePayload = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "DELIVERED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fullUpdatePayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotPatchStatus() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String customerToken = tokenFor(Role.CUSTOMER);
        Long productId = createProduct(adminToken, "SKU-ORD-CUST-STATUS-1");

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String statusPayload = """
                {
                  "status": "SHIPPED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", id)
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanOnlySeeOwnOrders() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-OWN-1");

        String customerAUsername = createUser(Role.CUSTOMER);
        String customerAToken = token(customerAUsername);
        String customerBToken = tokenFor(Role.CUSTOMER);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customerAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customerBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .header("Authorization", "Bearer " + customerAToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + customerBToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"id\":" + orderId))));
    }

    @Test
    void noResponseBodyContainsPassword() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        Long productId = createProduct(adminToken, "SKU-ORD-NOPASS-1");

        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();

        org.hamcrest.MatcherAssert.assertThat(response, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password")));
    }
}
