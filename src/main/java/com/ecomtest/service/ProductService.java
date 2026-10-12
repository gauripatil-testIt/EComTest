package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.security.AuthenticatedUserProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public ProductService(ProductRepository productRepository, UserRepository userRepository,
                           AuthenticatedUserProvider authenticatedUserProvider) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        User currentUser = currentUser();
        product.setCreatedBy(currentUser);
        product.setModifiedBy(currentUser);
        return productRepository.save(product);
    }

    public Product get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        if (isCustomer() && product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product not found: " + id);
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
        product.setModifiedBy(currentUser());
        return productRepository.save(product);
    }

    public void delete(Long id) {
        Product product = get(id);
        productRepository.delete(product);
    }

    private boolean isCustomer() {
        return authenticatedUserProvider.currentRole() == Role.CUSTOMER;
    }

    private User currentUser() {
        String username = authenticatedUserProvider.currentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).orElse(null);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());
    }
}

