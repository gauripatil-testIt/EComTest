package com.ecomtest.dto;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderResponse {

    private Long id;
    private String customerName;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private OrderStatus status;
    private String createdBy;
    private String modifiedBy;
    private Instant createdAt;
    private Instant modifiedAt;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.customerName = order.getCustomerName();
        response.productId = order.getProduct().getId();
        response.quantity = order.getQuantity();
        response.unitPrice = order.getUnitPrice();
        response.status = order.getStatus();
        response.createdBy = order.getCreatedBy();
        response.modifiedBy = order.getModifiedBy();
        response.createdAt = order.getCreatedAt();
        response.modifiedAt = order.getModifiedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }
}
