package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        String username = currentUsername();
        product.setCreatedBy(username);
        product.setCreatedAt(Instant.now());
        return productRepository.save(product);
    }

    public Product get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (isCustomer() && product.getStatus() != ProductStatus.ACTIVE) {
            throw new AccessDeniedException("Customers may only view active products");
        }
        return product;
    }

    public List<Product> list() {
        List<Product> products = productRepository.findAll();
        if (isCustomer()) {
            return products.stream()
                    .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                    .toList();
        }
        return products;
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        applyRequest(product, request);
        product.setModifiedBy(currentUsername());
        product.setModifiedAt(Instant.now());
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    private boolean isCustomer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER"));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }
}

