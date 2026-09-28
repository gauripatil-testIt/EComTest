package com.ecomtest.dto;

import com.ecomtest.entity.ImageStatus;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.LocalImageStorageService;

public class ProductImageResponse {

    private Long id;
    private int displayOrder;
    private boolean primary;
    private ImageStatus status;
    private String thumbnailUrl;
    private String originalUrl;

    public static ProductImageResponse from(ProductImage image, LocalImageStorageService storageService) {
        ProductImageResponse response = new ProductImageResponse();
        response.id = image.getId();
        response.displayOrder = image.getDisplayOrder();
        response.primary = image.isPrimary();
        response.status = image.getStatus();
        if (image.getStatus() == ImageStatus.READY) {
            response.thumbnailUrl = storageService.resolveUrl(image.getThumbnailKey());
            response.originalUrl = storageService.resolveUrl(image.getOriginalKey());
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public boolean getIsPrimary() {
        return primary;
    }

    public ImageStatus getStatus() {
        return status;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }
}
