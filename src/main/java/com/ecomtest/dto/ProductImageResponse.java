package com.ecomtest.dto;

import com.ecomtest.entity.ProductImage;

public class ProductImageResponse {

    private Long id;
    private Long productId;
    private Integer displayOrder;
    private Boolean isMain;
    private String thumbnailUrl;
    private String mediumUrl;
    private String fullUrl;

    public static ProductImageResponse from(ProductImage image, String baseUrl) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.productId = image.getProduct().getId();
        response.displayOrder = image.getDisplayOrder();
        response.isMain = image.getIsMain();
        response.thumbnailUrl = toUrl(baseUrl, image.getThumbnailPath());
        response.mediumUrl = toUrl(baseUrl, image.getMediumPath());
        response.fullUrl = toUrl(baseUrl, image.getFullPath());
        return response;
    }

    private static String toUrl(String baseUrl, String relativePath) {
        if (relativePath == null) {
            return null;
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl + relativePath;
        }
        return baseUrl + "/" + relativePath;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public Boolean getIsMain() {
        return isMain;
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
