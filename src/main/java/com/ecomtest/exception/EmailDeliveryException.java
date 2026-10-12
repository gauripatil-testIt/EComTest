package com.ecomtest.exception;

/**
 * Raised when rendering or sending an order status notification email fails.
 * Thrown as unchecked so it can propagate through Spring Retry's {@code @Retryable}
 * machinery without the caller having to declare it.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
