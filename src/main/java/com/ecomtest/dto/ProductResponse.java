package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;
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
        return from(product, List.of());
    }

    public static ProductResponse from(Product product, List<ProductImageResponse> images) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.status = product.getStatus();
        response.images = images;
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
