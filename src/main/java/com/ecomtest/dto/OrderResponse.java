package com.ecomtest.dto;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;

import java.math.BigDecimal;

public class OrderResponse {

    private Long id;
    private String customerName;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private OrderStatus status;
    private Long customerUserId;
    private String createdByUsername;
    private String modifiedByUsername;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.customerName = order.getCustomerName();
        response.productId = order.getProduct().getId();
        response.quantity = order.getQuantity();
        response.unitPrice = order.getUnitPrice();
        response.status = order.getStatus();
        response.customerUserId = order.getCustomerUser() != null ? order.getCustomerUser().getId() : null;
        response.createdByUsername = order.getCreatedBy() != null ? order.getCreatedBy().getUsername() : null;
        response.modifiedByUsername = order.getModifiedBy() != null ? order.getModifiedBy().getUsername() : null;
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

    public Long getCustomerUserId() {
        return customerUserId;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public String getModifiedByUsername() {
        return modifiedByUsername;
    }
}
