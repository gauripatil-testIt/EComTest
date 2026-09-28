package com.ecomtest.event;

import com.ecomtest.entity.OrderStatus;

import java.math.BigDecimal;

public record OrderStatusChangedEvent(
        Long orderId,
        OrderStatus previousStatus,
        OrderStatus newStatus,
        String customerEmail,
        String customerName,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        String trackingNumber
) {
}
