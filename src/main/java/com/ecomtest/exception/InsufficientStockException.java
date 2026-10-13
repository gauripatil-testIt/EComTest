package com.ecomtest.exception;

public class InsufficientStockException extends RuntimeException {

    private final Integer requestedQuantity;
    private final Integer availableStock;

    public InsufficientStockException(String message, Integer requestedQuantity, Integer availableStock) {
        super(message);
        this.requestedQuantity = requestedQuantity;
        this.availableStock = availableStock;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public Integer getAvailableStock() {
        return availableStock;
    }
}
