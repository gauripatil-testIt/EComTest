package com.ecomtest.repository;

import com.ecomtest.dto.ProductFilter;
import com.ecomtest.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepositoryCustom {

    Page<Product> search(String term, ProductFilter filter, Pageable pageable);
}
