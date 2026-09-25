package com.ecomtest.controller;

import com.ecomtest.TestcontainersConfiguration;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String sku, String name, String description, String price, String stock, String status) {
        return """
                {
                  "name": "%s",
                  "sku": "%s",
                  "description": "%s",
                  "price": %s,
                  "stock": %s,
                  "status": "%s"
                }
                """.formatted(name, sku, description, price, stock, status);
    }

    private void createProduct(String sku, String name, String description, String price, String stock, String status) throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, name, description, price, stock, status)))
                .andExpect(status().isCreated());
    }

    @Test
    void searchRanksPartialWordMatchesAboveUnrelatedProducts() throws Exception {
        createProduct("SKU-SEARCH-LAPTOP-1", "Laptop Pro", "A great laptop for professionals", "999.99", "10", "ACTIVE");
        createProduct("SKU-SEARCH-UNRELATED-1", "Desk Chair", "An ergonomic office chair", "199.99", "10", "ACTIVE");

        mockMvc.perform(get("/api/products/search").param("q", "lapto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.content[0].name").value("Laptop Pro"))
                .andExpect(jsonPath("$.content[0].nameHighlight").exists());
    }

    @Test
    void searchWithoutQFallsBackToFilteredPaginatedListing() throws Exception {
        createProduct("SKU-SEARCH-FILTER-1", "Filtered Widget", "A widget in range", "20.00", "5", "ACTIVE");

        mockMvc.perform(get("/api/products/search")
                        .param("status", "ACTIVE")
                        .param("minPrice", "10")
                        .param("maxPrice", "50")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].nameHighlight").doesNotExist())
                .andExpect(jsonPath("$.content[0].relevanceScore").doesNotExist());
    }

    @Test
    void searchWithNoMatchesReturnsEmptyContentAndZeroTotalElements() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "doesnotexist-unique-term-zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.empty()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
