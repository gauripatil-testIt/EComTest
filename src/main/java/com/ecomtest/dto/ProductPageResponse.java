package com.ecomtest.dto;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public class ProductPageResponse {

    private List<ProductResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static ProductPageResponse from(Page<Product> productPage) {
        ProductPageResponse response = new ProductPageResponse();
        response.content = productPage.getContent().stream().map(ProductResponse::from).toList();
        response.page = productPage.getNumber();
        response.size = productPage.getSize();
        response.totalElements = productPage.getTotalElements();
        response.totalPages = productPage.getTotalPages();
        return response;
    }

    public static ProductPageResponse fromSearch(Page<ProductSearchProjection> searchPage) {
        ProductPageResponse response = new ProductPageResponse();
        response.content = searchPage.getContent().stream().map(ProductPageResponse::toResponse).toList();
        response.page = searchPage.getNumber();
        response.size = searchPage.getSize();
        response.totalElements = searchPage.getTotalElements();
        response.totalPages = searchPage.getTotalPages();
        return response;
    }

    private static ProductResponse toResponse(ProductSearchProjection projection) {
        Product product = new Product();
        product.setId(projection.getId());
        product.setName(projection.getName());
        product.setSku(projection.getSku());
        product.setDescription(projection.getDescription());
        product.setPrice(projection.getPrice());
        product.setStock(projection.getStock());
        product.setStatus(projection.getStatus() == null ? null : ProductStatus.valueOf(projection.getStatus()));

        ProductResponse response = ProductResponse.from(product);
        response.setNameHighlight(projection.getNameHighlight());
        response.setSkuHighlight(projection.getSkuHighlight());
        response.setDescriptionHighlight(projection.getDescriptionHighlight());
        response.setRelevanceScore(projection.getRank());
        return response;
    }

    public List<ProductResponse> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }
}
