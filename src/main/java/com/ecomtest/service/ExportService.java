package com.ecomtest.service;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.Product;
import com.ecomtest.repository.OrderRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExportService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public ExportService(OrderRepository orderRepository, ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    public String ordersCsv() {
        List<Order> orders = orderRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("id,customerName,productId,quantity,unitPrice,status,createdBy,modifiedBy\n");
        for (Order order : orders) {
            csv.append(order.getId()).append(',')
                    .append(escape(order.getCustomerName())).append(',')
                    .append(order.getProduct() != null ? order.getProduct().getId() : "").append(',')
                    .append(order.getQuantity()).append(',')
                    .append(order.getUnitPrice()).append(',')
                    .append(order.getStatus()).append(',')
                    .append(escape(order.getCreatedBy())).append(',')
                    .append(escape(order.getModifiedBy())).append('\n');
        }
        return csv.toString();
    }

    public String productsCsv() {
        List<Product> products = productRepository.findAll();
        StringBuilder csv = new StringBuilder();
        csv.append("id,name,sku,price,stock,status,createdBy,modifiedBy\n");
        for (Product product : products) {
            csv.append(product.getId()).append(',')
                    .append(escape(product.getName())).append(',')
                    .append(escape(product.getSku())).append(',')
                    .append(product.getPrice()).append(',')
                    .append(product.getStock()).append(',')
                    .append(product.getStatus()).append(',')
                    .append(escape(product.getCreatedBy())).append(',')
                    .append(escape(product.getModifiedBy())).append('\n');
        }
        return csv.toString();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
