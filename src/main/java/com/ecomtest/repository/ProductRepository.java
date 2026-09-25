package com.ecomtest.repository;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.stream.Stream;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatus(ProductStatus status);

    @Query("select p from Product p where p.status = :status")
    Stream<Product> streamForExport(ProductStatus status);
}
