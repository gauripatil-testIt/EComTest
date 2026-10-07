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

    private Long createProduct(String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private Long createProduct(String sku, int stock) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, stock)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private int getProductStock(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/products/{id}", productId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("stock").asInt();
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

    private String orderPayload(Long productId, int quantity) {
        return """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": %d,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId, quantity);
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
    void rejectsOrderExceedingAvailableStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-1", 3);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient stock"))
                .andExpect(jsonPath("$.requested").value(5))
                .andExpect(jsonPath("$.available").value(3));

        assertEquals(3, getProductStock(productId));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptsOrderForExactlyAvailableStockAndDecrementsToZero() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-2", 3);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 3)))
                .andExpect(status().isCreated());

        assertEquals(0, getProductStock(productId));
    }

    @Test
    void decrementsStockWhenOrderAccepted() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-3", 10);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andExpect(status().isCreated());

        assertEquals(6, getProductStock(productId));
    }

    @Test
    void cancellingOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-CANCEL-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        assertEquals(6, getProductStock(productId));

        mockMvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(10, getProductStock(productId));
    }

    @Test
    void cancellingAlreadyCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-CANCEL-2", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk());

        assertEquals(10, getProductStock(productId));

        mockMvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk());

        assertEquals(10, getProductStock(productId));
    }

    @Test
    void increasingOrderQuantityDecrementsStockByExtraUnitsOnly() throws Exception {
        Long productId = createProduct("SKU-ORD-UPDATE-STOCK-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        assertEquals(6, getProductStock(productId));

        String updatedPayload = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 6,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(6));

        assertEquals(4, getProductStock(productId));
    }

    @Test
    void decreasingOrderQuantityIncrementsStockByFreedUnits() throws Exception {
        Long productId = createProduct("SKU-ORD-UPDATE-STOCK-2", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 6)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        assertEquals(4, getProductStock(productId));

        String updatedPayload = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 2,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        assertEquals(8, getProductStock(productId));
    }

    @Test
    void increasingOrderQuantityBeyondAvailableStockIsRefusedAndLeavesStateUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-UPDATE-STOCK-3", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        assertEquals(6, getProductStock(productId));

        String updatedPayload = """
                {
                  "customerName": "Jane Doe",
                  "productId": %d,
                  "quantity": 20,
                  "unitPrice": 19.99,
                  "status": "PENDING"
                }
                """.formatted(productId);

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient stock"))
                .andExpect(jsonPath("$.requested").value(16))
                .andExpect(jsonPath("$.available").value(6));

        assertEquals(6, getProductStock(productId));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(4));
    }

    @Test
    void deletingNonCancelledOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-DELETE-STOCK-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        assertEquals(6, getProductStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(10, getProductStock(productId));
    }

    @Test
    void deletingAlreadyCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-DELETE-STOCK-2", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, 4)))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(post("/api/orders/{id}/cancel", id))
                .andExpect(status().isOk());

        assertEquals(10, getProductStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(10, getProductStock(productId));
    }
}
