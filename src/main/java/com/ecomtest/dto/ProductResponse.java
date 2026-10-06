package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.entity.ProcessingStatus;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private List<ProductImageResponse> images;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.status = product.getStatus();

        List<ProductImage> productImages = product.getImages();
        if (productImages == null) {
            response.images = List.of();
        } else {
            response.images = productImages.stream()
                    .filter(image -> image.getStatus() == ProcessingStatus.READY)
                    .sorted(Comparator.comparing(ProductImage::getDisplayOrder))
                    .map(image -> ProductImageResponse.from(image, product.getId()))
                    .toList();
        }
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public List<ProductImageResponse> getImages() {
        return images;
    }
}
