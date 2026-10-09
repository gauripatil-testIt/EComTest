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
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void filtersOrdersByStatus() throws Exception {
        Long productId = createProduct("SKU-ORD-STATUS-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("PENDING"))));
    }

    @Test
    void filtersOrdersByCustomerNamePartialMatch() throws Exception {
        Long productId = createProduct("SKU-ORD-CUST-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders").param("customerName", "jane"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void filtersOrdersByProductId() throws Exception {
        Long productId = createProduct("SKU-ORD-PRODUCTID-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        mockMvc.perform(get("/api/orders").param("productId", String.valueOf(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].productId", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(productId.intValue()))));
    }

    @Test
    void filtersOrdersByDateRange() throws Exception {
        Long productId = createProduct("SKU-ORD-DATE-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId)));

        java.time.Instant from = java.time.Instant.now().minusSeconds(3600);
        java.time.Instant to = java.time.Instant.now().plusSeconds(3600);

        mockMvc.perform(get("/api/orders")
                        .param("dateFrom", from.toString())
                        .param("dateTo", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void rejectsUnknownOrderStatusFilter() throws Exception {
        mockMvc.perform(get("/api/orders").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsEmptyContentWhenNoOrdersMatch() throws Exception {
        mockMvc.perform(get("/api/orders").param("customerName", "no-such-customer-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()));
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
}
