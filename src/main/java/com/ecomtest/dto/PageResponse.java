package com.ecomtest.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public class PageResponse<T> {

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        PageResponse<T> response = new PageResponse<>();
        response.content = page.getContent().stream().map(mapper).toList();
        response.page = page.getNumber();
        response.size = page.getSize();
        response.totalElements = page.getTotalElements();
        response.totalPages = page.getTotalPages();
        return response;
    }

    public List<T> getContent() {
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
