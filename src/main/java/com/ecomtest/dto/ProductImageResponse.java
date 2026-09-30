package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private Long imageId;
    private Boolean isMain;
    private Integer sortOrder;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;

    public static ProductImageResponse from(ProductImage image) {
        ProductImageResponse response = new ProductImageResponse();
        Long productId = image.getProduct().getId();
        response.imageId = image.getId();
        response.isMain = image.getIsMain();
        response.sortOrder = image.getSortOrder();
        response.thumbnailUrl = "/api/products/" + productId + "/images/" + image.getId() + "/thumbnail";
        response.mediumUrl = "/api/products/" + productId + "/images/" + image.getId() + "/medium";
        response.fullUrl = "/api/products/" + productId + "/images/" + image.getId() + "/full";
        return response;
    }

    public Long getImageId() {
        return imageId;
    }

    public Boolean getIsMain() {
        return isMain;
    }

    public Integer getSortOrder() {
        return sortOrder;
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
