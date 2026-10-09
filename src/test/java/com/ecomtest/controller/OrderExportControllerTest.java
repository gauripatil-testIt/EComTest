package com.ecomtest.controller;

import tools.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderExportControllerTest {

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

    @Test
    @WithMockUser(authorities = "EXPORT_ORDERS")
    void exportsOrdersAsCsv() throws Exception {
        Long productId = createProduct("SKU-ORD-EXPORT-CSV-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId, "Jane Doe", 2, "PENDING")));

        String csv = mockMvc.perform(get("/api/orders/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"orders-export-\\d+\\.csv\"")))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\n");
        assertEquals("id,customerName,productName,sku,quantity,unitPrice,lineTotal,status", lines[0].trim());
        assertThat(csv, containsString("Jane Doe"));
        assertThat(csv, containsString("SKU-ORD-EXPORT-CSV-1"));
        assertThat(csv, containsString("39.98"));
    }

    @Test
    @WithMockUser(authorities = "EXPORT_ORDERS")
    void exportsOrdersFilteredByStatus() throws Exception {
        Long productId = createProduct("SKU-ORD-EXPORT-FILTER-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId, "Jane Doe", 1, "PENDING")));
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId, "John Roe", 1, "CONFIRMED")));

        String csv = mockMvc.perform(get("/api/orders/export")
                        .param("format", "csv")
                        .param("status", "CONFIRMED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv, containsString("John Roe"));
        assertThat(csv, not(containsString("Jane Doe")));
    }

    @Test
    @WithMockUser(authorities = "EXPORT_ORDERS")
    void exportsOrdersAsXlsxWithNumericCells() throws Exception {
        Long productId = createProduct("SKU-ORD-EXPORT-XLSX-1");

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderPayload(productId, "Jane Doe", 2, "PENDING")));

        byte[] xlsx = mockMvc.perform(get("/api/orders/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"orders-export-\\d+\\.xlsx\"")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals("id", header.getCell(0).getStringCellValue());
            assertEquals("quantity", header.getCell(4).getStringCellValue());
            assertEquals("unitPrice", header.getCell(5).getStringCellValue());
            assertEquals("lineTotal", header.getCell(6).getStringCellValue());

            Row dataRow = workbook.getSheetAt(0).getRow(1);
            Cell quantityCell = dataRow.getCell(4);
            Cell unitPriceCell = dataRow.getCell(5);
            Cell lineTotalCell = dataRow.getCell(6);
            assertEquals(CellType.NUMERIC, quantityCell.getCellType());
            assertEquals(CellType.NUMERIC, unitPriceCell.getCellType());
            assertEquals(CellType.NUMERIC, lineTotalCell.getCellType());
        }
    }

    @Test
    @WithMockUser(authorities = "EXPORT_ORDERS")
    void rejectsUnsupportedFormat() throws Exception {
        mockMvc.perform(get("/api/orders/export").param("format", "pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/api/orders/export").param("format", "csv"))
                .andExpect(status().isForbidden());
    }
}
