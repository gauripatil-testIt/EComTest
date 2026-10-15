package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

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

    private Long createProduct(String sku, String token) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId) {
        return """
                {
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);
    }

    private String loginPayload(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }

    private String login(String username, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin", "admin123");
    }

    private String customerToken() throws Exception {
        return login("customer", "customer123");
    }

    @Test
    void createsAndFetchesOrder() throws Exception {
        String admin = adminToken();
        String customer = customerToken();
        Long productId = createProduct("SKU-ORD-CREATE-1", admin);

        String response = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.customerName").value("customer"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("customer"));
    }

    @Test
    void listsOrders() throws Exception {
        String admin = adminToken();
        String customer = customerToken();
        Long productId = createProduct("SKU-ORD-LIST-1", admin);

        mockMvc.perform(post("/api/orders")
                .header("Authorization", "Bearer " + customer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesOrder() throws Exception {
        String admin = adminToken();
        String customer = customerToken();
        Long productId = createProduct("SKU-ORD-UPDATE-1", admin);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        String updatedPayload = """
                {
                  "productId": %d,
                  "quantity": 5,
                  "unitPrice": 19.99,
                  "status": "CONFIRMED"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("customer"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesOrder() throws Exception {
        String admin = adminToken();
        String customer = customerToken();
        Long productId = createProduct("SKU-ORD-DELETE-1", admin);

        String created = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderWithUnknownProduct() throws Exception {
        String customer = customerToken();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L)))
                .andExpect(status().isNotFound());
    }
}
