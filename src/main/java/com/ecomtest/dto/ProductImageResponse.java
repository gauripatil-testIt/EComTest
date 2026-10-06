package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private Long id;
    private Boolean isMain;
    private Integer displayOrder;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;

    public static ProductImageResponse from(ProductImage image, Long productId) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.isMain = image.getIsMain();
        response.displayOrder = image.getDisplayOrder();
        String base = "/api/products/" + productId + "/images/" + image.getId() + "/";
        response.thumbnailUrl = base + "thumbnail";
        response.mediumUrl = base + "medium";
        response.fullUrl = base + "full";
        return response;
    }

    public Long getId() {
        return id;
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
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
