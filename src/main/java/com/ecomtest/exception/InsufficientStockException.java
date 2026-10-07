package com.ecomtest.exception;

public class InsufficientStockException extends RuntimeException {

    private final Integer requested;
    private final Integer available;

    public InsufficientStockException(Integer requested, Integer available) {
        super("Insufficient stock");
        this.requested = requested;
        this.available = available;
    }

    public Integer getRequested() {
        return requested;
    }

    public Integer getAvailable() {
        return available;
    }
}
