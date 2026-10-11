package com.ecomtest.exception;

public class ImageValidationException extends RuntimeException {

    private final ImageErrorCode code;

    public ImageValidationException(ImageErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ImageErrorCode getCode() {
        return code;
    }
}
