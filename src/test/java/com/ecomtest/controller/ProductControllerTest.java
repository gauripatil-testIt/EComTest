package com.ecomtest.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.bootstrap.admin.username}")
    private String adminUsername;

    @Value("${app.bootstrap.admin.password}")
    private String adminPassword;

    private String adminToken() throws Exception {
        return TestAuthSupport.loginToken(mockMvc, objectMapper, adminUsername, adminPassword);
    }

    private String staffToken(String username) throws Exception {
        String body = """
                {
                  "username": "%s",
                  "password": "staffpass",
                  "role": "STAFF"
                }
                """.formatted(username);
        mockMvc.perform(post("/api/users")
                        .headers(TestAuthSupport.bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        return TestAuthSupport.loginToken(mockMvc, objectMapper, username, "staffpass");
    }

    private String customerToken(String username) throws Exception {
        return TestAuthSupport.registerToken(mockMvc, objectMapper, username, "customerpass");
    }

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

    private Long createProduct(String sku) throws Exception {
        return createProduct(sku, "ACTIVE");
    }

    private Long createProduct(String sku, String status) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .headers(TestAuthSupport.bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, status)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsAndFetchesProduct() throws Exception {
        String token = adminToken();

        String response = mockMvc.perform(post("/api/products")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-CREATE-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-CREATE-1"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void listsProducts() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/products")
                .headers(TestAuthSupport.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-LIST-1")));

        mockMvc.perform(get("/api/products")
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesProduct() throws Exception {
        String token = adminToken();

        String created = mockMvc.perform(post("/api/products")
                        .headers(TestAuthSupport.bearer(token))
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
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse Pro"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    void deletesProduct() throws Exception {
        String token = adminToken();

        String created = mockMvc.perform(post("/api/products")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-DELETE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedProductRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void staffCannotDeleteProduct() throws Exception {
        Long productId = createProduct("SKU-STAFF-DELETE-1");
        String staff = staffToken("staff-delete-product");

        mockMvc.perform(delete("/api/products/{id}", productId)
                        .headers(TestAuthSupport.bearer(staff)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSeesOnlyActiveProducts() throws Exception {
        String activeSku = "SKU-CUST-ACTIVE-1";
        String inactiveSku = "SKU-CUST-INACTIVE-1";
        createProduct(activeSku, "ACTIVE");
        createProduct(inactiveSku, "INACTIVE");
        String customer = customerToken("customer-products");

        String response = mockMvc.perform(get("/api/products")
                        .headers(TestAuthSupport.bearer(customer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode products = objectMapper.readTree(response);
        Set<String> skus = new HashSet<>();
        for (int i = 0; i < products.size(); i++) {
            skus.add(products.get(i).get("sku").asText());
        }
        assertTrue(skus.contains(activeSku), "CUSTOMER should see ACTIVE products");
        assertFalse(skus.contains(inactiveSku), "CUSTOMER must not see INACTIVE products");
    }
}
