package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
        return objectMapper.readTree(response).get("token").asText();
    }

    private String authHeader(String username, String password) throws Exception {
        return "Bearer " + login(username, password);
    }

    private String adminAuthHeader() throws Exception {
        return authHeader("admin", "Admin@123");
    }

    private String createUserAndGetAuthHeader(String role) throws Exception {
        String username = "pct-" + role.toLowerCase() + "-" + COUNTER.incrementAndGet();
        String password = "Password@123";
        String payload = """
                {
                  "username": "%s",
                  "password": "%s",
                  "role": "%s"
                }
                """.formatted(username, password, role);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", adminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        return authHeader(username, password);
    }

    private Long createProduct(String authHeader, String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", authHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsAndFetchesProduct() throws Exception {
        String adminAuth = adminAuthHeader();

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
        String adminAuth = adminAuthHeader();

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
        String adminAuth = adminAuthHeader();

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
        String adminAuth = adminAuthHeader();

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
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotSeeInactiveProducts() throws Exception {
        String adminAuth = adminAuthHeader();
        Long activeId = createProduct(adminAuth, "SKU-CUST-ACTIVE-1");

        String inactivePayload = """
                {
                  "name": "Discontinued Widget",
                  "sku": "SKU-CUST-INACTIVE-1",
                  "price": 9.99,
                  "stock": 0,
                  "status": "DISCONTINUED"
                }
                """;
        String inactiveResponse = mockMvc.perform(post("/api/products")
                        .header("Authorization", adminAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inactivePayload))
                .andReturn().getResponse().getContentAsString();
        Long inactiveId = objectMapper.readTree(inactiveResponse).get("id").asLong();

        String customerAuth = createUserAndGetAuthHeader("CUSTOMER");

        mockMvc.perform(get("/api/products/{id}", activeId)
                        .header("Authorization", customerAuth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/{id}", inactiveId)
                        .header("Authorization", customerAuth))
                .andExpect(status().isNotFound());
    }
}
