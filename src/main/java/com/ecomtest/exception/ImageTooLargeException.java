package com.ecomtest.exception;

public class ImageTooLargeException extends RuntimeException {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    public ImageTooLargeException(String message) {
        super(message);
    }
}
