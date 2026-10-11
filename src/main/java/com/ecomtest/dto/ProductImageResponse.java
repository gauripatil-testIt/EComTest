package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private static final String BASE_URL = "/uploads/products/";

    private Long id;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;
    private Integer displayOrder;
    private boolean mainImage;

    public static ProductImageResponse from(ProductImage image) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.thumbnailUrl = BASE_URL + image.getThumbnailPath();
        response.mediumUrl = BASE_URL + image.getMediumPath();
        response.fullUrl = BASE_URL + image.getFullPath();
        response.displayOrder = image.getDisplayOrder();
        response.mainImage = image.isMainImage();
        return response;
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
