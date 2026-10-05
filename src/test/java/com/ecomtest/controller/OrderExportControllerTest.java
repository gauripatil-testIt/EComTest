package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private String adminAuthHeader() throws Exception {
        return "Bearer " + login("admin", "admin123");
    }

    private String staffAuthHeader() throws Exception {
        return "Bearer " + login("staff", "staff123");
    }

    private String customerAuthHeader() throws Exception {
        return "Bearer " + login("customer", "customer123");
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

    private Long createProduct(String sku, String auth) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, auth)
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
    void adminCanExportOrdersAsCsv() throws Exception {
        String admin = adminAuthHeader();
        Long productId = createProduct("SKU-EXPORT-1", admin);

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/export")
                        .header(HttpHeaders.AUTHORIZATION, admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id,customerName,productId,quantity,unitPrice,status,createdByUserId,modifiedByUserId")))
                .andExpect(content().string(containsString("Jane Doe")));
    }

    @Test
    void staffCannotExportOrders() throws Exception {
        String staff = staffAuthHeader();

        mockMvc.perform(get("/api/orders/export")
                        .header(HttpHeaders.AUTHORIZATION, staff))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotExportOrders() throws Exception {
        String customer = customerAuthHeader();

        mockMvc.perform(get("/api/orders/export")
                        .header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotExportOrders() throws Exception {
        mockMvc.perform(get("/api/orders/export"))
                .andExpect(status().isUnauthorized());
    }
}
