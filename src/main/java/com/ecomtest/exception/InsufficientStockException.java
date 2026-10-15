package com.ecomtest.exception;

public class InsufficientStockException extends RuntimeException {

    private final int requested;
    private final int available;

    public InsufficientStockException(int requested, int available) {
        super("Insufficient stock: requested " + requested + ", available " + available);
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
