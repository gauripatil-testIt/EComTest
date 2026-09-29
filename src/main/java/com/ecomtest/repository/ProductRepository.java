package com.ecomtest.repository;

import com.ecomtest.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    java.util.List<Product> findAllByStatus(com.ecomtest.entity.ProductStatus status);
}
