package com.ecomtest.exception;

public class UnsupportedExportFormatException extends RuntimeException {

    public UnsupportedExportFormatException(String format) {
        super("Unsupported export format: " + format);
    }
}
