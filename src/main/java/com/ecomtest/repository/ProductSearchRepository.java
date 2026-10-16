package com.ecomtest.repository;

import com.ecomtest.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface ProductSearchRepository {

    Page<ProductSearchRow> search(List<String> tokens, ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
