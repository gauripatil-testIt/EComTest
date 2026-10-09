package com.ecomtest.service.export;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.repository.OrderRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Stream;

@Service
public class OrderExportService {

    private final OrderRepository orderRepository;

    public OrderExportService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public void writeTo(OrderStatus status, ExportFormat format, OutputStream out) {
        switch (format) {
            case CSV -> writeCsv(status, out);
            case XLSX -> writeXlsx(status, out);
        }
    }

    public String buildFilename(ExportFormat format) {
        String extension = format == ExportFormat.CSV ? "csv" : "xlsx";
        return "orders-export-" + Instant.now().toEpochMilli() + "." + extension;
    }

    private BigDecimal lineTotal(Order order) {
        return order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
    }

    private void writeCsv(OrderStatus status, OutputStream out) {
        try {
            Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            writer.write("id,customerName,productName,sku,quantity,unitPrice,lineTotal,status\n");
            try (Stream<Order> orders = orderRepository.streamForExport(status)) {
                orders.forEach(order -> {
                    try {
                        writer.write(toCsvRow(order));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toCsvRow(Order order) {
        return String.join(",",
                csvEscape(String.valueOf(order.getId())),
                csvEscape(order.getCustomerName()),
                csvEscape(order.getProduct().getName()),
                csvEscape(order.getProduct().getSku()),
                csvEscape(String.valueOf(order.getQuantity())),
                csvEscape(order.getUnitPrice().toPlainString()),
                csvEscape(lineTotal(order).toPlainString()),
                csvEscape(order.getStatus().name())
        ) + "\n";
    }

    private String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeXlsx(OrderStatus status, OutputStream out) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Orders");
            Row header = sheet.createRow(0);
            String[] columns = {"id", "customerName", "productName", "sku", "quantity", "unitPrice", "lineTotal", "status"};
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            int[] rowNum = {1};
            try (Stream<Order> orders = orderRepository.streamForExport(status)) {
                orders.forEach(order -> {
                    Row row = sheet.createRow(rowNum[0]++);
                    row.createCell(0).setCellValue(order.getId());
                    row.createCell(1).setCellValue(order.getCustomerName());
                    row.createCell(2).setCellValue(order.getProduct().getName());
                    row.createCell(3).setCellValue(order.getProduct().getSku());
                    row.createCell(4).setCellValue(order.getQuantity());
                    row.createCell(5).setCellValue(order.getUnitPrice().doubleValue());
                    row.createCell(6).setCellValue(lineTotal(order).doubleValue());
                    row.createCell(7).setCellValue(order.getStatus().name());
                });
            }

            workbook.write(out);
            workbook.dispose();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
