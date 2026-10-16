package com.ecomtest.dto;

import java.util.List;

public class ProductSearchResponse {

    private final List<ProductSearchResultResponse> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final int totalPages;

    public ProductSearchResponse(List<ProductSearchResultResponse> content, int page, int size, long totalElements, int totalPages) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<ProductSearchResultResponse> getContent() {
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
