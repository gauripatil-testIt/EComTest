package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSearchResultResponse {

    private final Long id;
    private final String name;
    private final String sku;
    private final BigDecimal price;
    private final Integer stock;
    private final ProductStatus status;
    private final List<String> matchedFields;

    public ProductSearchResultResponse(Long id, String name, String sku, BigDecimal price, Integer stock, ProductStatus status, List<String> matchedFields) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.stock = stock;
        this.status = status;
        this.matchedFields = matchedFields;
    }

    public static ProductSearchResultResponse from(Product product, boolean nameMatched, boolean skuMatched) {
        List<String> matchedFields = new ArrayList<>();
        if (nameMatched) {
            matchedFields.add("name");
        }
        if (skuMatched) {
            matchedFields.add("sku");
        }
        return new ProductSearchResultResponse(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getPrice(),
                product.getStock(),
                product.getStatus(),
                List.copyOf(matchedFields));
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

    public List<String> getMatchedFields() {
        return matchedFields;
    }
}
