package com.ecomtest.dto;

import com.ecomtest.entity.Order;
import com.ecomtest.entity.OrderStatus;

import java.math.BigDecimal;

public class OrderResponse {

    private Long id;
    private String customerName;
    private Long customerId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private OrderStatus status;
    private String createdBy;
    private String modifiedBy;
    private String lastStatusChangedBy;

    public static OrderResponse from(Order order) {
        OrderResponse response = new OrderResponse();
        response.id = order.getId();
        response.customerName = order.getCustomerName();
        response.customerId = order.getCustomerId();
        response.productId = order.getProduct().getId();
        response.quantity = order.getQuantity();
        response.unitPrice = order.getUnitPrice();
        response.status = order.getStatus();
        response.createdBy = order.getCreatedBy();
        response.modifiedBy = order.getModifiedBy();
        response.lastStatusChangedBy = order.getLastStatusChangedBy();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Long getCustomerId() {
        return customerId;
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

    public String getLastStatusChangedBy() {
        return lastStatusChangedBy;
    }
}
