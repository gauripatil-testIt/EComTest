package com.ecomtest.export;

public enum ExportFormat {
    CSV,
    XLSX;

    public static ExportFormat fromParam(String format) {
        if (format == null) {
            throw new com.ecomtest.exception.UnsupportedExportFormatException(format);
        }
        for (ExportFormat value : values()) {
            if (value.name().equalsIgnoreCase(format)) {
                return value;
            }
        }
        throw new com.ecomtest.exception.UnsupportedExportFormatException(format);
    }
}
