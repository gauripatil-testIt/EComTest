package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private String description;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.status = product.getStatus();
        response.description = product.getDescription();
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

    public String getDescription() {
        return description;
    }
}
