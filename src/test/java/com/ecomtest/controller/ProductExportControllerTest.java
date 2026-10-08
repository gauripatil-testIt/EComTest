package com.ecomtest.controller;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.repository.ProductRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ProductExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private static final String EXPORTER_AUTH = basicAuth("exporter", "password");
    private static final String VIEWER_AUTH = basicAuth("viewer", "password");

    private static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private Product createProduct(String sku, ProductStatus status) {
        Product product = new Product();
        product.setName("Export Test Product");
        product.setSku(sku);
        product.setPrice(new BigDecimal("12.50"));
        product.setStock(42);
        product.setStatus(status);
        return productRepository.save(product);
    }

    @Test
    void csvExportHasExpectedHeaderAndColumns() throws Exception {
        createProduct("SKU-EXPORT-CSV-1", ProductStatus.ACTIVE);

        MvcResult result = mockMvc.perform(get("/api/products/export")
                        .param("format", "csv")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"products-export-")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String headerLine = body.split("\n", 2)[0].trim();
        assertEquals("id,name,sku,price,stock,status", headerLine);
        assertTrue(body.contains("SKU-EXPORT-CSV-1"));
    }

    @Test
    void xlsxExportUsesNumericCellsForNumericColumns() throws Exception {
        createProduct("SKU-EXPORT-XLSX-1", ProductStatus.ACTIVE);

        MvcResult result = mockMvc.perform(get("/api/products/export")
                        .param("format", "xlsx")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("price", header.getCell(3).getStringCellValue());
            assertEquals("stock", header.getCell(4).getStringCellValue());

            Row dataRow = findRowBySku(sheet, "SKU-EXPORT-XLSX-1");
            assertNotNull(dataRow, "expected to find exported row for SKU-EXPORT-XLSX-1");
            assertEquals(CellType.NUMERIC, dataRow.getCell(3).getCellType());
            assertEquals(CellType.NUMERIC, dataRow.getCell(4).getCellType());
        }
    }

    private Row findRowBySku(Sheet sheet, String sku) {
        for (Row row : sheet) {
            Cell cell = row.getCell(2);
            if (cell != null && cell.getCellType() == CellType.STRING && sku.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    @Test
    void unsupportedFormatReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/products/export")
                        .param("format", "bogus")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/products/export").param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWithoutExportAuthorityIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/export")
                        .param("format", "csv")
                        .header("Authorization", VIEWER_AUTH))
                .andExpect(status().isForbidden())
                .andReturn();

        // Ensure no export content is returned when forbidden
        assertTrue(result.getResponse().getContentAsString().isBlank());
    }

    @Test
    void statusFilterExcludesNonMatchingProducts() throws Exception {
        createProduct("SKU-EXPORT-FILTER-ACTIVE", ProductStatus.ACTIVE);
        createProduct("SKU-EXPORT-FILTER-INACTIVE", ProductStatus.INACTIVE);

        String body = mockMvc.perform(get("/api/products/export")
                        .param("format", "csv")
                        .param("status", "ACTIVE")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("SKU-EXPORT-FILTER-ACTIVE"));
        assertFalse(body.contains("SKU-EXPORT-FILTER-INACTIVE"));
    }

    @Test
    void exportSpansMultiplePagesForLargeDatasets() throws Exception {
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            Product product = new Product();
            product.setName("Bulk Export Product");
            product.setSku("SKU-EXPORT-BULK-" + i);
            product.setPrice(BigDecimal.ONE);
            product.setStock(1);
            product.setStatus(ProductStatus.DISCONTINUED);
            products.add(product);
        }
        productRepository.saveAll(products);

        String body = mockMvc.perform(get("/api/products/export")
                        .param("format", "csv")
                        .param("status", "DISCONTINUED")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // header + 600 data rows: the service's internal page size is 500, so this
        // dataset only exports in full if the export loop advances past the first page.
        long dataRows = body.lines().count() - 1;
        assertEquals(600, dataRows);
    }
}
