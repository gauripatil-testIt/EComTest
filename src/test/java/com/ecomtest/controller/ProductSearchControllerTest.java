package com.ecomtest.controller;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String name, String sku, String price, int stock, String status) {
        return """
                {
                  "name": "%s",
                  "sku": "%s",
                  "price": %s,
                  "stock": %d,
                  "status": "%s"
                }
                """.formatted(name, sku, price, stock, status);
    }

    private Long createProduct(String name, String sku, String price, int stock, String status) throws Exception {
        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(name, sku, price, stock, status)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private JsonNode search(String query) throws Exception {
        String response = mockMvc.perform(get("/api/products/search").param("q", query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode findContentItem(JsonNode body, long id) {
        JsonNode content = body.get("content");
        for (int i = 0; i < content.size(); i++) {
            JsonNode node = content.get(i);
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    private int indexOfContentItem(JsonNode body, long id) {
        JsonNode content = body.get("content");
        for (int i = 0; i < content.size(); i++) {
            if (content.get(i).get("id").asLong() == id) {
                return i;
            }
        }
        return -1;
    }

    private List<String> matchedFields(JsonNode item) {
        List<String> fields = new ArrayList<>();
        JsonNode matched = item.get("matchedFields");
        for (int i = 0; i < matched.size(); i++) {
            fields.add(matched.get(i).asText());
        }
        return fields;
    }

    @Test
    void searchMatchesNamePrefixCaseInsensitively() throws Exception {
        Long productId = createProduct("Laptop Pro", "NAME-PREFIX-1001", "1299.00", 5, "ACTIVE");

        JsonNode body = search("lapto");

        JsonNode item = findContentItem(body, productId);
        assertNotNull(item, "expected product " + productId + " to match q=lapto");
        assertEquals("Laptop Pro", item.get("name").asText());
        assertEquals(List.of("name"), matchedFields(item));
    }

    @Test
    void searchMatchesSkuPrefix() throws Exception {
        Long productId = createProduct("Desk Organizer", "LAPTOP-SKU-7001", "39.99", 12, "ACTIVE");

        JsonNode body = search("laptop-s");

        JsonNode item = findContentItem(body, productId);
        assertNotNull(item, "expected product " + productId + " to match q=laptop-s");
        assertEquals(List.of("sku"), matchedFields(item));
    }

    @Test
    void searchRanksNameMatchAboveSkuOnlyMatch() throws Exception {
        Long skuOnlyId = createProduct("Pro Notebook Stand", "LAPTO-RANK-9001", "89.00", 20, "ACTIVE");
        Long nameMatchId = createProduct("Laptop Rank Pro", "RANK-9002", "149.00", 8, "ACTIVE");

        JsonNode body = search("lapto");

        int nameIndex = indexOfContentItem(body, nameMatchId);
        int skuIndex = indexOfContentItem(body, skuOnlyId);
        assertTrue(nameIndex >= 0, "name match should be present in results");
        assertTrue(skuIndex >= 0, "sku-only match should be present in results");
        assertTrue(nameIndex < skuIndex, "name match should rank before sku-only match");
    }

    @Test
    void multiWordSearchRequiresEveryToken() throws Exception {
        Long matchingId = createProduct("Wireless Mouse", "MOUSE-WM-3001", "29.99", 15, "ACTIVE");
        Long missingTokenId = createProduct("Wireless Keyboard", "KB-WK-3002", "59.99", 15, "ACTIVE");

        JsonNode body = search("wireless mouse");

        JsonNode matchingItem = findContentItem(body, matchingId);
        assertNotNull(matchingItem, "Wireless Mouse should match both tokens");
        assertEquals("Wireless Mouse", matchingItem.get("name").asText());
        assertNull(findContentItem(body, missingTokenId), "Wireless Keyboard should not match the 'mouse' token");
    }

    @Test
    void searchCombinesWithFiltersAndPagination() throws Exception {
        Long firstId = createProduct("Filtered Alpha", "FLT-A-1", "100.00", 10, "ACTIVE");
        Long secondId = createProduct("Filtered Beta", "FLT-B-1", "200.00", 10, "ACTIVE");
        createProduct("Filtered Below Min", "FLT-C-1", "40.00", 10, "ACTIVE");
        createProduct("Filtered Above Max", "FLT-D-1", "300.00", 10, "ACTIVE");
        createProduct("Filtered Inactive", "FLT-E-1", "150.00", 10, "INACTIVE");

        String page0 = mockMvc.perform(get("/api/products/search")
                        .param("q", "filtered")
                        .param("status", "ACTIVE")
                        .param("minPrice", "80")
                        .param("maxPrice", "250")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        assertEquals(firstId.longValue(), objectMapper.readTree(page0).get("content").get(0).get("id").asLong());

        String page1 = mockMvc.perform(get("/api/products/search")
                        .param("q", "filtered")
                        .param("status", "ACTIVE")
                        .param("minPrice", "80")
                        .param("maxPrice", "250")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        assertEquals(secondId.longValue(), objectMapper.readTree(page1).get("content").get(0).get("id").asLong());
    }

    @Test
    void blankOrMissingQueryFallsBackToPagedListing() throws Exception {
        createProduct("Fallback Listing Product", "FALLBACK-8001", "9.99", 3, "ACTIVE");

        mockMvc.perform(get("/api/products/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.totalElements", org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(get("/api/products/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())));
    }

    @Test
    void searchWithNoResultsReturnsEmptyContent() throws Exception {
        mockMvc.perform(get("/api/products/search").param("q", "zzzz-no-match-9x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }
}
