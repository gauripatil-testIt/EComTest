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

    private Long createProduct(String sku) throws Exception {
        String response = mockMvc.perform(post("/api/products")
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

    private Long createProduct(String sku, int stock) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, stock)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private String orderPayload(Long productId, String customerName, int quantity, String status) {
        return """
                {
                  "customerName": "%s",
                  "productId": %d,
                  "quantity": %d,
                  "unitPrice": 19.99,
                  "status": "%s"
                }
                """.formatted(customerName, productId, quantity, status);
    }

    private int productStock(Long productId) throws Exception {
        String response = mockMvc.perform(get("/api/products/{id}", productId))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("stock").asInt();
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
    void refusesOrderExceedingStockAndCreatesNone() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-REFUSE-1", 3);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Refused Stock Customer", 500, "PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: requested 500, available 3"));

        assertEquals(3, productStock(productId));

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.customerName == 'Refused Stock Customer')]",
                        org.hamcrest.Matchers.empty()));
    }

    @Test
    void stockFallsWhenOrderAccepted() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-FALL-1", 100);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Stock Fall Customer", 2, "PENDING")))
                .andExpect(status().isCreated());

        assertEquals(98, productStock(productId));
    }

    @Test
    void orderForExactRemainingStockAccepted() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-EXACT-1", 3);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Exact Stock Customer", 3, "PENDING")))
                .andExpect(status().isCreated());

        assertEquals(0, productStock(productId));
    }

    @Test
    void increaseQuantityDeductsExtraOnly() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-INCREASE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Increase Customer", 2, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(8, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Increase Customer", 5, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(5, productStock(productId));
    }

    @Test
    void decreaseQuantityRestoresReleasedUnits() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DECREASE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Decrease Customer", 5, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(5, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Decrease Customer", 2, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(8, productStock(productId));
    }

    @Test
    void increaseBeyondStockRefusedLeavesStateUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-REFUSE-INC-1", 5);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Refuse Increase Customer", 2, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(3, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Refuse Increase Customer", 10, "PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: requested 10, available 5"));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertEquals(3, productStock(productId));
    }

    @Test
    void cancellingOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-CANCEL-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Cancel Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(7, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Cancel Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(10, productStock(productId));
    }

    @Test
    void cancellingAlreadyCancelledLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-CANCEL-TWICE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Cancel Twice Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Cancel Twice Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());
        assertEquals(10, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Cancel Twice Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());

        assertEquals(10, productStock(productId));
    }

    @Test
    void deletingNonCancelledOrderRestoresStock() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DELETE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Delete Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(7, productStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(10, productStock(productId));
    }

    @Test
    void deletingCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-DELETE-CANCELLED-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Delete Cancelled Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Delete Cancelled Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());
        assertEquals(10, productStock(productId));

        mockMvc.perform(delete("/api/orders/{id}", id))
                .andExpect(status().isNoContent());

        assertEquals(10, productStock(productId));
    }

    @Test
    void creatingCancelledOrderLeavesStockUnchanged() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-CREATE-CANCELLED-1", 10);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Create Cancelled Customer", 5, "CANCELLED")))
                .andExpect(status().isCreated());

        assertEquals(10, productStock(productId));
    }

    @Test
    void reactivatingCancelledOrderDeductsAgain() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-REACTIVATE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(7, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());
        assertEquals(10, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Customer", 3, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(7, productStock(productId));
    }

    @Test
    void reactivatingCancelledOrderBeyondStockRefused() throws Exception {
        Long productId = createProduct("SKU-ORD-STOCK-REACTIVATE-REFUSE-1", 10);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Refuse Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Refuse Customer", 3, "CANCELLED")))
                .andExpect(status().isOk());
        assertEquals(10, productStock(productId));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productId, "Reactivate Refuse Customer", 11, "PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: requested 11, available 10"));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(10, productStock(productId));
    }

    @Test
    void changingProductMovesStockBetweenProducts() throws Exception {
        Long productA = createProduct("SKU-ORD-STOCK-MOVE-A-1", 10);
        Long productB = createProduct("SKU-ORD-STOCK-MOVE-B-1", 5);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productA, "Move Product Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(7, productStock(productA));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productB, "Move Product Customer", 3, "PENDING")))
                .andExpect(status().isOk());

        assertEquals(10, productStock(productA));
        assertEquals(2, productStock(productB));
    }

    @Test
    void changingProductToInsufficientProductRefusedLeavesBothUnchanged() throws Exception {
        Long productA = createProduct("SKU-ORD-STOCK-MOVE-REFUSE-A-1", 10);
        Long productB = createProduct("SKU-ORD-STOCK-MOVE-REFUSE-B-1", 5);

        String created = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productA, "Move Refuse Customer", 3, "PENDING")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();
        assertEquals(7, productStock(productA));

        mockMvc.perform(put("/api/orders/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderPayload(productB, "Move Refuse Customer", 7, "PENDING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: requested 7, available 5"));

        mockMvc.perform(get("/api/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productA))
                .andExpect(jsonPath("$.quantity").value(3));
        assertEquals(7, productStock(productA));
        assertEquals(5, productStock(productB));
    }
}
