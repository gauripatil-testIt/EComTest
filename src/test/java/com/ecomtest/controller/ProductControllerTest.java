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
class ProductControllerTest {

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

    @Test
    void createsAndFetchesProduct() throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-CREATE-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-CREATE-1"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse"));
    }

    @Test
    void listsProducts() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-LIST-1")));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.page").exists())
                .andExpect(jsonPath("$.size").exists());
    }

    @Test
    void filtersAndPaginatesProducts() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Alpha Widget",
                          "sku": "SKU-FILTER-ALPHA",
                          "price": 15.00,
                          "stock": 10,
                          "status": "ACTIVE"
                        }
                        """));

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Beta Widget",
                          "sku": "SKU-FILTER-BETA",
                          "price": 5.00,
                          "stock": 0,
                          "status": "INACTIVE"
                        }
                        """));

        mockMvc.perform(get("/api/products")
                        .param("status", "ACTIVE")
                        .param("minPrice", "10")
                        .param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku=='SKU-FILTER-ALPHA')]").exists())
                .andExpect(jsonPath("$.content[?(@.sku=='SKU-FILTER-BETA')]").doesNotExist());

        mockMvc.perform(get("/api/products")
                        .param("inStock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.sku=='SKU-FILTER-BETA')]").doesNotExist());

        mockMvc.perform(get("/api/products")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0));

        mockMvc.perform(get("/api/products")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/products")
                        .param("status", "ACTIVE")
                        .param("minPrice", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()));
    }

    @Test
    void updatesProduct() throws Exception {
        String created = mockMvc.perform(post("/api/products")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Wireless Mouse Pro"))
                .andExpect(jsonPath("$.stock").value(50));
    }

    @Test
    void deletesProduct() throws Exception {
        String created = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-DELETE-1")))
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(delete("/api/products/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound());
    }
}
