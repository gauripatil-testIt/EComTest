package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.dto.ProductSearchResponse;
import com.ecomtest.dto.ProductSearchResultResponse;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.ProductSearchRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
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

    public ProductSearchResponse search(String q, ProductStatus status, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        List<String> tokens = tokenize(q);
        Page<ProductSearchRow> page = productRepository.search(tokens, status, minPrice, maxPrice, pageable);
        List<ProductSearchResultResponse> content = page.getContent().stream()
                .map(row -> ProductSearchResultResponse.from(row.product(), row.nameMatched(), row.skuMatched()))
                .toList();
        return new ProductSearchResponse(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private List<String> tokenize(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : q.trim().split("\\s+")) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }
}
