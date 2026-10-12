package com.ecomtest.dto;

import com.ecomtest.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

public class OrderStatusRequest {

    @NotNull
    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
