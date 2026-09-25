package com.ecomtest.service;

import com.ecomtest.dto.ProductPageResponse;
import com.ecomtest.dto.ProductRequest;
import com.ecomtest.dto.ProductSearchProjection;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    public ProductPageResponse search(String q, ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        if (q == null || q.isBlank()) {
            Page<Product> page = productRepository.findByFilters(status, minPrice, maxPrice, pageable);
            return ProductPageResponse.from(page);
        }
        Page<ProductSearchProjection> page = productRepository.searchByRelevance(q, status, minPrice, maxPrice, pageable);
        return ProductPageResponse.fromSearch(page);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }
}
