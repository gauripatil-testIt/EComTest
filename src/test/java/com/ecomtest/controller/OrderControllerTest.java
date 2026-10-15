package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
                        .headers(TestAuthSupport.bearer(token))
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

    private Long createOrder(String token, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsAndFetchesOrder() throws Exception {
        String token = adminToken();
        Long productId = createProduct("SKU-ORD-CREATE-1", token);

        String response = mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"));
    }

    @Test
    void listsOrders() throws Exception {
        String token = adminToken();
        Long productId = createProduct("SKU-ORD-LIST-1", token);

        mockMvc.perform(post("/api/orders")
                .headers(TestAuthSupport.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders")
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesOrder() throws Exception {
        String token = adminToken();
        Long productId = createProduct("SKU-ORD-UPDATE-1", token);

        String created = mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(token))
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
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Smith"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesOrder() throws Exception {
        String token = adminToken();
        Long productId = createProduct("SKU-ORD-DELETE-1", token);

        String created = mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id)
                        .headers(TestAuthSupport.bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderWithUnknownProduct() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedOrderRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotSeeOtherCustomersOrders() throws Exception {
        String admin = adminToken();
        Long productId = createProduct("SKU-ORD-CUST-A-1", admin);
        String customerA = customerToken("customer-order-a");
        String customerB = customerToken("customer-order-b");

        String created = mockMvc.perform(post("/api/orders")
                        .headers(TestAuthSupport.bearer(customerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long orderId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", orderId)
                        .headers(TestAuthSupport.bearer(customerB)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanPatchOrderStatus() throws Exception {
        String admin = adminToken();
        Long productId = createProduct("SKU-ORD-STAFF-PATCH-1", admin);
        Long orderId = createOrder(admin, productId);
        String staff = staffToken("staff-patch-order");

        String statusPayload = """
                {
                  "status": "SHIPPED"
                }
                """;

        mockMvc.perform(patch("/api/orders/{id}/status", orderId)
                        .headers(TestAuthSupport.bearer(staff))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(statusPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));
    }

    @Test
    void customerCannotDeleteOrder() throws Exception {
        String admin = adminToken();
        Long productId = createProduct("SKU-ORD-CUST-DELETE-1", admin);
        String customer = customerToken("customer-delete-order");
        Long orderId = createOrder(customer, productId);

        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .headers(TestAuthSupport.bearer(customer)))
                .andExpect(status().isForbidden());
    }
}
