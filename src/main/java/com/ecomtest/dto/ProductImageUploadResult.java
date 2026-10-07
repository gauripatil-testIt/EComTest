package com.ecomtest.dto;

import java.util.ArrayList;
import java.util.List;

public class ProductImageUploadResult {

    private final List<ProductImageResponse> uploaded = new ArrayList<>();
    private final List<ProductImageUploadError> errors = new ArrayList<>();

    public void addUploaded(ProductImageResponse response) {
        uploaded.add(response);
    }

    public void addError(String filename, String message) {
        errors.add(new ProductImageUploadError(filename, message));
    }

    public List<ProductImageResponse> getUploaded() {
        return uploaded;
    }

    public List<ProductImageUploadError> getErrors() {
        return errors;
    }

    public static class ProductImageUploadError {
        private final String filename;
        private final String message;

        public ProductImageUploadError(String filename, String message) {
            this.filename = filename;
            this.message = message;
        }

        public String getFilename() {
            return filename;
        }

        public String getMessage() {
            return message;
        }
    }
}
