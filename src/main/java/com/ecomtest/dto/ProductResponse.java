package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;

public class ProductResponse {

    private Long id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private String nameHighlight;
    private String skuHighlight;
    private String descriptionHighlight;
    private Double relevanceScore;

    public static ProductResponse from(Product product) {
        ProductResponse response = new ProductResponse();
        response.id = product.getId();
        response.name = product.getName();
        response.sku = product.getSku();
        response.description = product.getDescription();
        response.price = product.getPrice();
        response.stock = product.getStock();
        response.status = product.getStatus();
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

    public String getDescription() {
        return description;
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

    public String getNameHighlight() {
        return nameHighlight;
    }

    public void setNameHighlight(String nameHighlight) {
        this.nameHighlight = nameHighlight;
    }

    public String getSkuHighlight() {
        return skuHighlight;
    }

    public void setSkuHighlight(String skuHighlight) {
        this.skuHighlight = skuHighlight;
    }

    public String getDescriptionHighlight() {
        return descriptionHighlight;
    }

    public void setDescriptionHighlight(String descriptionHighlight) {
        this.descriptionHighlight = descriptionHighlight;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
