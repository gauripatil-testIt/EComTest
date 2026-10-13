package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private Long id;
    private int sortOrder;
    private boolean main;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;

    public static ProductImageResponse from(ProductImage image) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.sortOrder = image.getSortOrder();
        response.main = image.isMain();
        response.thumbnailUrl = toUrl(image.getThumbnailPath());
        response.mediumUrl = toUrl(image.getMediumPath());
        response.fullUrl = toUrl(image.getFullPath());
        return response;
    }

    private static String toUrl(String path) {
        if (path == null) {
            return null;
        }
        String normalized = path.replace("\\", "/");
        int idx = normalized.indexOf("uploads/products");
        String relevant = idx >= 0 ? normalized.substring(idx) : normalized;
        return "/" + relevant;
    }

    public Long getId() {
        return id;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isMain() {
        return main;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getMediumUrl() {
        return mediumUrl;
    }

    public String getFullUrl() {
        return fullUrl;
    }
}
