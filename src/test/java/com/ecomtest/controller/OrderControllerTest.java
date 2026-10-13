package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String sku, int stock) {
        return """
                {
                  "name": "Wireless Mouse",
                  "sku": "%s",
                  "price": 19.99,
                  "stock": %d,
                  "status": "ACTIVE"
                }
                """.formatted(sku, stock);
    }

    private String productPayload(String sku) {
        return productPayload(sku, 100);
    }

    private Long createProduct(String sku, int stock) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, stock)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createProduct(String sku) throws Exception {
        return createProduct(sku, 100);
    }

    private int getProductStock(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/products/{id}", productId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("stock").asInt();
    }

    private int countOrders() throws Exception {
        String response = mockMvc.perform(get("/api/orders"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).size();
    }

    private String orderPayload(Long productId, int quantity, String status) {
        return """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": %d,
                  "unitPrice": 19.99,
                  "status": "%s"
                }
                """.formatted(productId, quantity, status);
    }

    private String orderPayload(Long productId) {
        return orderPayload(productId, 2, "PENDING");
    }

    private Long createOrder(Long productId, int quantity, String status) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, quantity, status)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    void createsAndFetchesOrder() throws Exception {
        Long productId = createProduct("SKU-ORD-CREATE-1");

        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Doe"));
    }

    @Test
    void listsOrders() throws Exception {
        Long productId = createProduct("SKU-ORD-LIST-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void updatesOrder() throws Exception {
        Long productId = createProduct("SKU-ORD-UPDATE-1");

        String created = mockMvc.perform(post("/api/orders")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane Smith"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void deletesOrder() throws Exception {
        Long productId = createProduct("SKU-ORD-DELETE-1");

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsOrderWithUnknownProduct() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(999999L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createWithInsufficientStockIsRejected() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-INSUFFICIENT-1", 100);
        int ordersBefore = countOrders();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 500, "PENDING")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.requestedQuantity").value(500))
                .andExpect(jsonPath("$.availableStock").value(100));

        assertEquals(ordersBefore, countOrders());
        assertEquals(100, getProductStock(productId));
    }

    @Test
    void createReducesProductStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-REDUCE-1", 100);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 10, "PENDING")))
                .andExpect(status().isCreated());

        assertEquals(90, getProductStock(productId));
    }

    @Test
    void createWithExactStockLeavesZeroStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-EXACT-1", 5);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 5, "PENDING")))
                .andExpect(status().isCreated());

        assertEquals(0, getProductStock(productId));
    }

    @Test
    void increasingQuantityReducesStockByDeltaOnly() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-INCREASE-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        assertEquals(90, getProductStock(productId));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 15, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(85, getProductStock(productId));
    }

    @Test
    void decreasingQuantityReturnsStockByDeltaOnly() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DECREASE-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        assertEquals(90, getProductStock(productId));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(96, getProductStock(productId));
    }

    @Test
    void increaseBeyondAvailableStockIsRejected() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-INCREASE-REJECT-1", 10);
        Long orderId = createOrder(productId, 10, "PENDING");

        assertEquals(0, getProductStock(productId));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 11, "PENDING")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(0, getProductStock(productId));
    }

    @Test
    void cancellingOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-CANCEL-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        assertEquals(90, getProductStock(productId));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 10, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(100, getProductStock(productId));
    }

    @Test
    void cancellingAlreadyCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-CANCEL-TWICE-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 10, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(100, getProductStock(productId));

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 10, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(100, getProductStock(productId));
    }

    @Test
    void deletingActiveOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DELETE-ACTIVE-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        assertEquals(90, getProductStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", orderId))
                .andExpect(status().isNoContent());

        assertEquals(100, getProductStock(productId));
    }

    @Test
    void deletingCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DELETE-CANCELLED-1", 100);
        Long orderId = createOrder(productId, 10, "PENDING");

        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 10, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(100, getProductStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", orderId))
                .andExpect(status().isNoContent());

        assertEquals(100, getProductStock(productId));
    }
}
