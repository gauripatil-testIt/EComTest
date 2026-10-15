package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private void createProduct(String name, String sku, BigDecimal price, int stock, String status) throws Exception {
        createProduct(name, sku, price, stock, status, null);
    }

    private void createProduct(String name, String sku, BigDecimal price, int stock, String status,
                               String description) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("sku", sku);
        payload.put("price", price);
        payload.put("stock", stock);
        payload.put("status", status);
        if (description != null) {
            payload.put("description", description);
        }

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated());
    }

    @Test
    void searchRanksNameAboveSkuAboveDescription() throws Exception {
        createProduct("ZebraX Pro", "SKU-SEARCH-RANK-NAME", new BigDecimal("19.99"), 10, "ACTIVE");
        createProduct("Plain Gadget", "SKU-ZEBRAX-SKU", new BigDecimal("19.99"), 10, "ACTIVE");
        createProduct("Plain Thing", "SKU-SEARCH-RANK-DESC", new BigDecimal("19.99"), 10, "ACTIVE",
                "Featuring ZebraX technology");

        mockMvc.perform(get("/api/products/search").param("q", "zebraX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("ZebraX Pro"))
                .andExpect(jsonPath("$.content[1].sku").value("SKU-ZEBRAX-SKU"))
                .andExpect(jsonPath("$.content[2].name").value("Plain Thing"));
    }

    @Test
    void searchSupportsPartialPrefixMatch() throws Exception {
        createProduct("Laptop", "SKU-SEARCH-PREFIX", new BigDecimal("999.99"), 5, "ACTIVE");

        mockMvc.perform(get("/api/products/search").param("q", "lapto"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Laptop"));
    }

    @Test
    void searchCombinesWithStatusAndPriceFilters() throws Exception {
        createProduct("FilterMatchQX One", "SKU-SEARCH-FILTER-A", new BigDecimal("30.00"), 10, "ACTIVE");
        createProduct("FilterMatchQX Two", "SKU-SEARCH-FILTER-B", new BigDecimal("30.00"), 10, "INACTIVE");
        createProduct("FilterMatchQX Three", "SKU-SEARCH-FILTER-C", new BigDecimal("90.00"), 10, "ACTIVE");

        mockMvc.perform(get("/api/products/search")
                        .param("q", "filterMatchQX")
                        .param("status", "ACTIVE")
                        .param("minPrice", "20")
                        .param("maxPrice", "80"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("FilterMatchQX One"));
    }

    @Test
    void searchPaginatesResults() throws Exception {
        createProduct("PageTerm Alpha", "SKU-SEARCH-PAGE-A", new BigDecimal("10.00"), 10, "ACTIVE");
        createProduct("PageTerm Beta", "SKU-SEARCH-PAGE-B", new BigDecimal("10.00"), 10, "ACTIVE");
        createProduct("PageTerm Gamma", "SKU-SEARCH-PAGE-C", new BigDecimal("10.00"), 10, "ACTIVE");

        mockMvc.perform(get("/api/products/search")
                        .param("q", "pageterm")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].name").value("PageTerm Alpha"))
                .andExpect(jsonPath("$.content[1].name").value("PageTerm Beta"));

        mockMvc.perform(get("/api/products/search")
                        .param("q", "pageterm")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].name").value("PageTerm Gamma"));
    }

    @Test
    void emptyQueryFallsBackToFilteredListing() throws Exception {
        createProduct("Fallback Product", "SKU-SEARCH-FALLBACK", new BigDecimal("15.00"), 10, "ACTIVE");

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/products/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void noResultsReturns200WithEmptyContent() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "ZZZZ-NO-MATCH-987654321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchReturnsMatchedFieldsAndHighlight() throws Exception {
        createProduct("HighlightMatch Widget", "SKU-SEARCH-HIGHLIGHT", new BigDecimal("12.50"), 10, "ACTIVE");

        mockMvc.perform(get("/api/products/search").param("q", "highlightmatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matchedFields[0]").value("name"))
                .andExpect(jsonPath("$.content[0].highlightSnippet").value(containsString("[[HighlightMatch]]")));
    }

    @Test
    void searchMatchesDescription() throws Exception {
        createProduct("No Match Name", "SKU-SEARCH-DESC-MATCH", new BigDecimal("25.00"), 10, "ACTIVE",
                "Premium quality laptop sleeve");

        mockMvc.perform(get("/api/products/search").param("q", "sleeve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].matchedFields[0]").value("description"))
                .andExpect(jsonPath("$.content[0].matchedFields.length()").value(1))
                .andExpect(jsonPath("$.content[0].highlightSnippet").value(containsString("[[sleeve]]")));
    }
}
