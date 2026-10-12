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
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String productPayload(String sku) {
        return productPayload(sku, "ACTIVE");
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

    private String authHeaderFor(String role) throws Exception {
        TestUserSeeder seeder = new TestUserSeeder(userRepository, passwordEncoder);
        String username = "test-" + role.toLowerCase() + "-" + UUID.randomUUID();
        switch (role) {
            case "ADMIN" -> seeder.seedAdmin(username);
            case "STAFF" -> seeder.seedStaff(username);
            case "CUSTOMER" -> seeder.seedCustomer(username);
            default -> throw new IllegalArgumentException("Unknown role: " + role);
        }
        return login(username);
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
    void createsAndFetchesProduct() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-CREATE-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-CREATE-1"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void listsProducts() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");

        mockMvc.perform(post("/api/products")
                .header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-LIST-1")));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", adminAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesProduct() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-UPDATE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String updatedPayload = """
                {
                  "name": "Wireless Mouse Pro",
                  "sku": "SKU-UPDATE-1",
                  "price": 24.99,
                  "stock": 50,
                  "status": "ACTIVE"
                }
                """;

        mockMvc.perform(put("/api/products/{id}", id)
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse Pro"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    void deletesProduct() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-DELETE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)
                        .header("Authorization", adminAuth))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-NOAUTH-1")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/products/{id}", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-NOAUTH-2")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/products/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProduct() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        String staffAuth = authHeaderFor("STAFF");

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-STAFF-DELETE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", staffAuth))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotSeeInactiveProducts() throws Exception {
        String adminAuth = authHeaderFor("ADMIN");
        String customerAuth = authHeaderFor("CUSTOMER");

        mockMvc.perform(post("/api/products")
                .header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-INACTIVE-1", "INACTIVE")));

        mockMvc.perform(post("/api/products")
                .header("Authorization", adminAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-ACTIVE-1", "ACTIVE")));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", customerAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.sku == 'SKU-INACTIVE-1')]").isEmpty())
                .andExpect(jsonPath("$[?(@.sku == 'SKU-ACTIVE-1')]").isNotEmpty());
    }
}
