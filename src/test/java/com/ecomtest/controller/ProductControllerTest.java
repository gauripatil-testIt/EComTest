package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
class ProductControllerTest {

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
        String username = "pc-" + role.name().toLowerCase() + "-" + COUNTER.incrementAndGet();
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

    @Test
    void createsAndFetchesProduct() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-CREATE-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-CREATE-1"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void listsProducts() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-LIST-1")));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesProduct() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
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
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse Pro"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    void deletesProduct() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-DELETE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnauthenticatedListRequest() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesDeleteForStaffAndCustomer() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String staffToken = tokenFor(Role.STAFF);
        String customerToken = tokenFor(Role.CUSTOMER);

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-DELETE-DENY-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void customerOnlySeesActiveProducts() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String customerToken = tokenFor(Role.CUSTOMER);

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-ACTIVE-1")));

        String inactivePayload = """
                {
                  "name": "Discontinued Widget",
                  "sku": "SKU-INACTIVE-1",
                  "price": 9.99,
                  "stock": 0,
                  "status": "DISCONTINUED"
                }
                """;
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(inactivePayload));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DISCONTINUED"))));
    }

    @Test
    void noResponseBodyContainsPassword() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);

        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-NOPASS-1")))
                .andReturn().getResponse().getContentAsString();

        org.hamcrest.MatcherAssert.assertThat(response, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password")));
    }
}
