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
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String name, String sku, String description, String price, String status) {
        return """
                {
                  "name": "%s",
                  "sku": "%s",
                  "description": "%s",
                  "price": %s,
                  "stock": 100,
                  "status": "%s"
                }
                """.formatted(name, sku, description, price, status);
    }

    private void createProduct(String name, String sku, String description, String price, String status) throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload(name, sku, description, price, status)));
    }

    @Test
    void searchMatchesPartialWord() throws Exception {
        createProduct("Laptop", "SKU-SEARCH-LAPTOP-1", "A powerful laptop computer", "999.99", "ACTIVE");

        mockMvc.perform(get("/api/products/search").param("q", "lapto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Laptop')]").exists());
    }

    @Test
    void searchRanksNameMatchAboveDescriptionMatch() throws Exception {
        String term = "zephyr";
        createProduct("Other Device", "SKU-SEARCH-DESC-1", "Contains the word " + term + " in description", "50.00", "ACTIVE");
        createProduct(term + " Speaker", "SKU-SEARCH-NAME-1", "A speaker product", "50.00", "ACTIVE");

        mockMvc.perform(get("/api/products/search").param("q", term))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value(term + " Speaker"));
    }

    @Test
    void searchWithNoMatchesReturnsEmptyContent() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "zzzz-no-match-zzzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()));
    }

    @Test
    void searchWithMissingQFallsBackToListing() throws Exception {
        createProduct("Fallback Widget", "SKU-SEARCH-FALLBACK-1", "A widget for fallback testing", "15.00", "ACTIVE");

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void searchWithEmptyQFallsBackToListing() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", ""))
                .andExpect(status().isOk());
    }

    @Test
    void searchCombinesWithFilters() throws Exception {
        createProduct("Mouse Pad", "SKU-SEARCH-FILTER-1", "A padded mouse surface", "20.00", "ACTIVE");
        createProduct("Mouse Pad Deluxe", "SKU-SEARCH-FILTER-2", "A deluxe padded mouse surface", "100.00", "ACTIVE");

        mockMvc.perform(get("/api/products/search")
                        .param("q", "mouse")
                        .param("status", "ACTIVE")
                        .param("minPrice", "10")
                        .param("maxPrice", "50")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.name == 'Mouse Pad')]").exists())
                .andExpect(jsonPath("$.content[?(@.name == 'Mouse Pad Deluxe')]").doesNotExist());
    }
}
