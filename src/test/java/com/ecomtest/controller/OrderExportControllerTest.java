package com.ecomtest.controller;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.repository.OrderRepository;
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
import java.util.Arrays;
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
class OrderExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    private static final String EXPORTER_AUTH = basicAuth("exporter", "password");
    private static final String VIEWER_AUTH = basicAuth("viewer", "password");

    private static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private Product createProduct(String sku, String name) {
        Product product = new Product();
        product.setName(name);
        product.setSku(sku);
        product.setPrice(new BigDecimal("19.99"));
        product.setStock(100);
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product);
    }

    private Order createOrder(String customerName, Product product, int quantity, BigDecimal unitPrice, OrderStatus status) {
        Order order = new Order();
        order.setCustomerName(customerName);
        order.setProduct(product);
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Test
    void csvExportHasExpectedHeaderColumnsAndComputedLineTotal() throws Exception {
        Product product = createProduct("SKU-ORDER-EXPORT-1", "Export Order Product");
        createOrder("Export Order Customer", product, 3, new BigDecimal("19.99"), OrderStatus.PENDING);

        MvcResult result = mockMvc.perform(get("/api/orders/export")
                        .param("format", "csv")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("attachment; filename=\"orders-export-")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String headerLine = body.split("\n", 2)[0].trim();
        assertEquals("id,customerName,productName,sku,quantity,unitPrice,lineTotal,status", headerLine);

        String dataRow = Arrays.stream(body.split("\n"))
                .filter(line -> line.contains("SKU-ORDER-EXPORT-1"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected exported row for SKU-ORDER-EXPORT-1"));
        String[] cols = dataRow.trim().split(",");
        assertEquals("Export Order Customer", cols[1]);
        assertEquals("Export Order Product", cols[2]);
        assertEquals("SKU-ORDER-EXPORT-1", cols[3]);
        assertEquals("3", cols[4]);
        assertEquals("19.99", cols[5]);
        assertEquals("59.97", cols[6]);
        assertEquals("PENDING", cols[7]);
    }

    @Test
    void xlsxExportUsesNumericCellsForNumericColumns() throws Exception {
        Product product = createProduct("SKU-ORDER-EXPORT-XLSX-1", "Export Order Product Xlsx");
        createOrder("Export Order Customer Xlsx", product, 2, new BigDecimal("10.00"), OrderStatus.PENDING);

        MvcResult result = mockMvc.perform(get("/api/orders/export")
                        .param("format", "xlsx")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("quantity", header.getCell(4).getStringCellValue());
            assertEquals("unitPrice", header.getCell(5).getStringCellValue());
            assertEquals("lineTotal", header.getCell(6).getStringCellValue());

            Row dataRow = findRowBySku(sheet, "SKU-ORDER-EXPORT-XLSX-1");
            assertNotNull(dataRow, "expected to find exported row for SKU-ORDER-EXPORT-XLSX-1");
            assertEquals(CellType.NUMERIC, dataRow.getCell(4).getCellType());
            assertEquals(CellType.NUMERIC, dataRow.getCell(5).getCellType());
            assertEquals(CellType.NUMERIC, dataRow.getCell(6).getCellType());
        }
    }

    private Row findRowBySku(Sheet sheet, String sku) {
        for (Row row : sheet) {
            Cell cell = row.getCell(3);
            if (cell != null && cell.getCellType() == CellType.STRING && sku.equals(cell.getStringCellValue())) {
                return row;
            }
        }
        return null;
    }

    @Test
    void unsupportedFormatReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/orders/export")
                        .param("format", "bogus")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders/export").param("format", "csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedWithoutExportAuthorityIsRejected() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/orders/export")
                        .param("format", "csv")
                        .header("Authorization", VIEWER_AUTH))
                .andExpect(status().isForbidden())
                .andReturn();

        // Ensure no export content is returned when forbidden
        assertTrue(result.getResponse().getContentAsString().isBlank());
    }

    @Test
    void statusFilterExcludesNonMatchingOrders() throws Exception {
        Product product = createProduct("SKU-ORDER-EXPORT-FILTER", "Export Filter Product");
        createOrder("Filter Match Customer", product, 1, new BigDecimal("5.00"), OrderStatus.DELIVERED);
        createOrder("Filter Nonmatch Customer", product, 1, new BigDecimal("5.00"), OrderStatus.CONFIRMED);

        String body = mockMvc.perform(get("/api/orders/export")
                        .param("format", "csv")
                        .param("status", "DELIVERED")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("Filter Match Customer"));
        assertFalse(body.contains("Filter Nonmatch Customer"));
    }

    @Test
    void exportSpansMultiplePagesForLargeDatasets() throws Exception {
        Product product = createProduct("SKU-ORDER-EXPORT-BULK", "Bulk Export Product");

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            Order order = new Order();
            order.setCustomerName("Bulk Customer " + i);
            order.setProduct(product);
            order.setQuantity(1);
            order.setUnitPrice(BigDecimal.ONE);
            order.setStatus(OrderStatus.CANCELLED);
            orders.add(order);
        }
        orderRepository.saveAll(orders);

        String body = mockMvc.perform(get("/api/orders/export")
                        .param("format", "csv")
                        .param("status", "CANCELLED")
                        .header("Authorization", EXPORTER_AUTH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // header + 600 data rows: the service's internal page size is 500, so this
        // dataset only exports in full if the export loop advances past the first page.
        long dataRows = body.lines().count() - 1;
        assertEquals(600, dataRows);
    }
}
