package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public class ProductSearchResult {

    private Long id;
    private String name;
    private String sku;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private ProductStatus status;
    private List<String> matchedFields;
    private String highlightSnippet;

    public static ProductSearchResult from(Product product, List<String> matchedFields, String highlightSnippet) {
        ProductSearchResult result = new ProductSearchResult();
        result.id = product.getId();
        result.name = product.getName();
        result.sku = product.getSku();
        result.description = product.getDescription();
        result.price = product.getPrice();
        result.stock = product.getStock();
        result.status = product.getStatus();
        result.matchedFields = matchedFields;
        result.highlightSnippet = highlightSnippet;
        return result;
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

    public List<String> getMatchedFields() {
        return matchedFields;
    }

    public String getHighlightSnippet() {
        return highlightSnippet;
    }
}
