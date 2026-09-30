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
    private Long createdByUserId;
    private Long modifiedByUserId;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.status = product.getStatus();
        response.createdByUserId = product.getCreatedBy() != null ? product.getCreatedBy().getId() : null;
        response.modifiedByUserId = product.getModifiedBy() != null ? product.getModifiedBy().getId() : null;
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

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Long getModifiedByUserId() {
        return modifiedByUserId;
    }
}
