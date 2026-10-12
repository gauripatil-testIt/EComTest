package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private Long id;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;
    private Integer displayOrder;
    private boolean mainImage;

    public static ProductImageResponse from(ProductImage image) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.thumbnailUrl = toUrl(image.getThumbnailPath());
        response.mediumUrl = toUrl(image.getMediumPath());
        response.fullUrl = toUrl(image.getFullPath());
        response.displayOrder = image.getDisplayOrder();
        response.mainImage = image.isMainImage();
        return response;
    }

    private static String toUrl(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        return "/uploads/" + relativePath;
    }

    public Long getId() {
        return id;
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

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isMainImage() {
        return mainImage;
    }
}
