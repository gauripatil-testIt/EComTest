package com.ecomtest.repository;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.stream.Stream;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByStatusOrderByIdAsc(ProductStatus status);

    List<Product> findAllByOrderByIdAsc();

    @Query("select p from Product p where (:status is null or p.status = :status) order by p.id asc")
    Stream<Product> streamForExport(@Param("status") ProductStatus status);
}
