package com.ecomtest.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageableUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageableUtils() {
    }

    public static Pageable toPageable(Integer page, Integer size, String sort, String defaultSortProperty) {
        int resolvedPage = page != null ? page : DEFAULT_PAGE;
        int resolvedSize = size != null ? size : DEFAULT_SIZE;
        if (resolvedSize > MAX_SIZE) {
            resolvedSize = MAX_SIZE;
        }
        if (resolvedSize < 1) {
            resolvedSize = 1;
        }
        if (resolvedPage < 0) {
            resolvedPage = 0;
        }

        Sort resolvedSort = parseSort(sort, defaultSortProperty);
        return PageRequest.of(resolvedPage, resolvedSize, resolvedSort);
    }

    private static Sort parseSort(String sort, String defaultSortProperty) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, defaultSortProperty);
        }

        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length > 1) {
            direction = Sort.Direction.fromString(parts[1].trim());
        }
        return Sort.by(direction, property);
    }
}
