package com.ecomtest.service;

import com.ecomtest.dto.ProductFilter;
import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

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
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }

    public Page<Product> list(ProductFilter filter, Pageable pageable) {
        Specification<Product> spec = buildSpecification(filter);
        return productRepository.findAll(spec, pageable);
    }

    public Page<Product> search(String q, ProductFilter filter, Pageable pageable) {
        if (q == null || q.isBlank()) {
            return list(filter, pageable);
        }
        return productRepository.search(q.trim(), filter, pageable);
    }

    private Specification<Product> buildSpecification(ProductFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.getStatus() != null) {
                    predicates.add(cb.equal(root.get("status"), filter.getStatus()));
                }
                if (filter.getMinPrice() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("price"), filter.getMinPrice()));
                }
                if (filter.getMaxPrice() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("price"), filter.getMaxPrice()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
