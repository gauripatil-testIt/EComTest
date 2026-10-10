package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import com.ecomtest.entity.Role;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.support.TestUserFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

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

    private String tokenFor(Role role) throws Exception {
        return TestUserFactory.createUserAndToken(mockMvc, objectMapper, userRepository, passwordEncoder, role);
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
    void unauthenticatedRequestToProductsReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotCreateUpdateOrDeleteProducts() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String staffToken = tokenFor(Role.STAFF);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-STAFF-CREATE-1")))
                .andExpect(status().isForbidden());

        String created = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-STAFF-UPDATE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + staffToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-STAFF-UPDATE-1")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/{id}", id)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerOnlySeesActiveProducts() throws Exception {
        String adminToken = tokenFor(Role.ADMIN);
        String customerToken = tokenFor(Role.CUSTOMER);

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-CUSTOMER-ACTIVE-1", "ACTIVE")));

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-CUSTOMER-INACTIVE-1", "INACTIVE")));

        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.status != 'ACTIVE')]").isEmpty());
    }
}
