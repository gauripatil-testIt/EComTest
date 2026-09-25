package com.ecomtest.export;

import com.ecomtest.exception.UnsupportedExportFormatException;
import org.springframework.http.MediaType;

public enum ExportFormat {

    CSV {
        @Override
        public MediaType contentType() {
            return new MediaType("text", "csv");
        }

        @Override
        public String fileExtension() {
            return "csv";
        }
    },
    XLSX {
        @Override
        public MediaType contentType() {
            return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Override
        public String fileExtension() {
            return "xlsx";
        }
    };

    public abstract MediaType contentType();

    public abstract String fileExtension();

    public static ExportFormat fromParam(String value) {
        if (value == null) {
            throw new UnsupportedExportFormatException("Unsupported export format: null");
        }
        for (ExportFormat format : values()) {
            if (format.name().equalsIgnoreCase(value)) {
                return format;
            }
        }
        throw new UnsupportedExportFormatException("Unsupported export format: " + value);
    }
}
