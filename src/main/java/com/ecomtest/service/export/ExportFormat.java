package com.ecomtest.service.export;

import com.ecomtest.exception.UnsupportedExportFormatException;

public enum ExportFormat {
    CSV,
    XLSX;

    public static ExportFormat fromParam(String value) {
        if (value == null) {
            throw new UnsupportedExportFormatException("null");
        }
        try {
            return ExportFormat.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new UnsupportedExportFormatException(value);
        }
    }
}
