package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;
import com.ecomtest.export.CsvExportWriter;
import com.ecomtest.export.ExportFormat;
import com.ecomtest.export.XlsxExportWriter;
import com.ecomtest.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;

@Service
public class OrderExportService {

    private static final int PAGE_SIZE = 500;
    private static final String[] COLUMNS =
            {"id", "customerName", "productName", "sku", "quantity", "unitPrice", "lineTotal", "status"};

    private final OrderRepository orderRepository;

    public OrderExportService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public void export(OrderStatus status, ExportFormat format, OutputStream out) throws IOException {
        if (format == ExportFormat.CSV) {
            exportCsv(status, out);
        } else {
            exportXlsx(status, out);
        }
    }

    private void exportCsv(OrderStatus status, OutputStream out) throws IOException {
        try (CsvExportWriter writer = new CsvExportWriter(out)) {
            writer.writeHeader(COLUMNS);
            int pageNumber = 0;
            Page<Order> page;
            do {
                page = fetchPage(status, pageNumber);
                for (Order order : page.getContent()) {
                    BigDecimal lineTotal = lineTotal(order);
                    writer.writeRow(
                            order.getId(),
                            order.getCustomerName(),
                            order.getProduct().getName(),
                            order.getProduct().getSku(),
                            order.getQuantity(),
                            order.getUnitPrice(),
                            lineTotal,
                            order.getStatus());
                }
                writer.flush();
                pageNumber++;
            } while (page.hasNext());
        }
    }

    private void exportXlsx(OrderStatus status, OutputStream out) throws IOException {
        try (XlsxExportWriter writer = new XlsxExportWriter("Orders")) {
            writer.writeHeader(COLUMNS);
            int pageNumber = 0;
            Page<Order> page;
            do {
                page = fetchPage(status, pageNumber);
                for (Order order : page.getContent()) {
                    BigDecimal lineTotal = lineTotal(order);
                    writer.writeRow(
                            order.getId(),
                            order.getCustomerName(),
                            order.getProduct().getName(),
                            order.getProduct().getSku(),
                            order.getQuantity(),
                            order.getUnitPrice(),
                            lineTotal,
                            order.getStatus() == null ? null : order.getStatus().name());
                }
                pageNumber++;
            } while (page.hasNext());
            writer.write(out);
        }
    }

    private BigDecimal lineTotal(Order order) {
        return order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
    }

    private Page<Order> fetchPage(OrderStatus status, int pageNumber) {
        PageRequest pageRequest = PageRequest.of(pageNumber, PAGE_SIZE);
        if (status == null) {
            return orderRepository.findAll(pageRequest);
        }
        return orderRepository.findByStatus(status, pageRequest);
    }
}
