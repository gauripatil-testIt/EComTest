package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.specification.ProductSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product get(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    public List<Product> list() {
        return productRepository.findAll();
    }

    public Page<Product> list(Pageable pageable, ProductStatus status, BigDecimal minPrice,
                               BigDecimal maxPrice, Boolean inStock) {
        Specification<Product> specification = Specification
                .allOf(
                        ProductSpecifications.hasStatus(status),
                        ProductSpecifications.priceGreaterOrEqual(minPrice),
                        ProductSpecifications.priceLessOrEqual(maxPrice),
                        ProductSpecifications.inStock(inStock)
                );
        return productRepository.findAll(specification, pageable);
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }
}
