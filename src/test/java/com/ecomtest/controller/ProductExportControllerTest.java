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
class ProductExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String sku, String status) {
        return """
                {
                  "name": "Wireless Mouse",
                  "sku": "%s",
                  "price": 19.99,
                  "stock": 100,
                  "status": "%s"
                }
                """.formatted(sku, status);
    }

    @Test
    @WithMockUser(authorities = "EXPORT_PRODUCTS")
    void exportsProductsAsCsv() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-EXPORT-CSV-1", "ACTIVE")));

        String csv = mockMvc.perform(get("/api/products/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"products-export-\\d+\\.csv\"")))
                .andReturn().getResponse().getContentAsString();

        String[] lines = csv.split("\n");
        assertEquals("id,name,sku,price,stock,status", lines[0].trim());
        assertThat(csv, containsString("SKU-EXPORT-CSV-1"));
    }

    @Test
    @WithMockUser(authorities = "EXPORT_PRODUCTS")
    void exportsProductsFilteredByStatus() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-EXPORT-FILTER-ACTIVE-1", "ACTIVE")));
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-EXPORT-FILTER-DISCONTINUED-1", "DISCONTINUED")));

        String csv = mockMvc.perform(get("/api/products/export")
                        .param("format", "csv")
                        .param("status", "DISCONTINUED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv, containsString("SKU-EXPORT-FILTER-DISCONTINUED-1"));
        assertThat(csv, not(containsString("SKU-EXPORT-FILTER-ACTIVE-1")));
    }

    @Test
    @WithMockUser(authorities = "EXPORT_PRODUCTS")
    void exportsProductsAsXlsxWithNumericCells() throws Exception {
        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(productPayload("SKU-EXPORT-XLSX-1", "ACTIVE")));

        byte[] xlsx = mockMvc.perform(get("/api/products/export").param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        matchesPattern("attachment; filename=\"products-export-\\d+\\.xlsx\"")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals("id", header.getCell(0).getStringCellValue());
            assertEquals("price", header.getCell(3).getStringCellValue());

            Row dataRow = workbook.getSheetAt(0).getRow(1);
            Cell priceCell = dataRow.getCell(3);
            Cell stockCell = dataRow.getCell(4);
            assertEquals(CellType.NUMERIC, priceCell.getCellType());
            assertEquals(CellType.NUMERIC, stockCell.getCellType());
        }
    }

    @Test
    @WithMockUser(authorities = "EXPORT_PRODUCTS")
    void rejectsUnsupportedFormat() throws Exception {
        mockMvc.perform(get("/api/products/export").param("format", "pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/api/products/export").param("format", "csv"))
                .andExpect(status().isForbidden());
    }
}
