package com.ecomtest.exception;

public class UnsupportedExportFormatException extends RuntimeException {

    private final String format;

    public UnsupportedExportFormatException(String format) {
        super("Unsupported export format: " + format);
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
}
