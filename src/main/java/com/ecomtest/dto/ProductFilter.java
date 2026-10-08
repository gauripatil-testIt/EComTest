package com.ecomtest.dto;

import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductFilter {

    private ProductStatus status;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    public ProductFilter() {
    }

    public ProductFilter(ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice) {
        this.status = status;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }
}
