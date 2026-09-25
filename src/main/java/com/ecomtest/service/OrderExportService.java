package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.export.ExportFormat;
import com.ecomtest.repository.OrderRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@Service
public class OrderExportService {

    private static final String[] HEADERS =
            {"id", "customerName", "productName", "sku", "quantity", "unitPrice", "lineTotal", "status"};
    private static final int XLSX_ROW_ACCESS_WINDOW = 100;

    private final OrderRepository orderRepository;

    public OrderExportService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public void write(OutputStream outputStream, ExportFormat format, OrderStatus status) throws IOException {
        switch (format) {
            case CSV -> writeCsv(outputStream, status);
            case XLSX -> writeXlsx(outputStream, status);
        }
    }

    private void writeCsv(OutputStream outputStream, OrderStatus status) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write(String.join(",", HEADERS));
        writer.write("\n");
        try (Stream<Order> orders = orderRepository.streamForExport(status)) {
            for (Order order : (Iterable<Order>) orders::iterator) {
                writer.write(csvRow(order));
                writer.write("\n");
            }
        }
        writer.flush();
    }

    private String csvRow(Order order) {
        BigDecimal lineTotal = lineTotal(order);
        return String.join(",",
                csvValue(order.getId()),
                csvValue(order.getCustomerName()),
                csvValue(order.getProduct() != null ? order.getProduct().getName() : null),
                csvValue(order.getProduct() != null ? order.getProduct().getSku() : null),
                csvValue(order.getQuantity()),
                csvValue(order.getUnitPrice()),
                csvValue(lineTotal),
                csvValue(order.getStatus()));
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private void writeXlsx(OutputStream outputStream, OrderStatus status) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(XLSX_ROW_ACCESS_WINDOW)) {
            SXSSFSheet sheet = workbook.createSheet("Orders");

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(HEADERS[i]);
            }

            int rowIndex = 1;
            try (Stream<Order> orders = orderRepository.streamForExport(status)) {
                for (Order order : (Iterable<Order>) orders::iterator) {
                    Row row = sheet.createRow(rowIndex++);
                    writeXlsxRow(row, order);
                }
            }

            workbook.write(outputStream);
            workbook.dispose();
        }
    }

    private void writeXlsxRow(Row row, Order order) {
        row.createCell(0).setCellValue(order.getId() == null ? 0d : order.getId().doubleValue());
        row.createCell(1).setCellValue(order.getCustomerName());
        row.createCell(2).setCellValue(order.getProduct() != null ? order.getProduct().getName() : "");
        row.createCell(3).setCellValue(order.getProduct() != null ? order.getProduct().getSku() : "");

        Cell quantityCell = row.createCell(4);
        if (order.getQuantity() != null) {
            quantityCell.setCellValue(order.getQuantity());
        }

        Cell unitPriceCell = row.createCell(5);
        if (order.getUnitPrice() != null) {
            unitPriceCell.setCellValue(order.getUnitPrice().doubleValue());
        }

        Cell lineTotalCell = row.createCell(6);
        BigDecimal lineTotal = lineTotal(order);
        if (lineTotal != null) {
            lineTotalCell.setCellValue(lineTotal.doubleValue());
        }

        row.createCell(7).setCellValue(order.getStatus() == null ? "" : order.getStatus().name());
    }

    private BigDecimal lineTotal(Order order) {
        if (order.getUnitPrice() == null || order.getQuantity() == null) {
            return null;
        }
        return order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
    }
}
