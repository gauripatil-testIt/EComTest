package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.repository.UserRepository;
import com.ecomtest.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public ProductService(ProductRepository productRepository, UserRepository userRepository) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        setAuditFields(product, true);
        return productRepository.save(product);
    }

    public Product get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        AppUserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == Role.CUSTOMER && product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResourceNotFoundException("Product not found: " + id);
        }
        return product;
    }

    public List<Product> list() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal != null && principal.getRole() == Role.CUSTOMER) {
            return productRepository.findAll().stream()
                    .filter(product -> product.getStatus() == ProductStatus.ACTIVE)
                    .toList();
        }
        return productRepository.findAll();
    }

    public Product update(Long id, ProductRequest request) {
        Product product = get(id);
        applyRequest(product, request);
        setAuditFields(product, false);
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

    private void setAuditFields(Product product, boolean isCreate) {
        User currentUser = currentUser();
        if (currentUser == null) {
            return;
        }
        if (isCreate) {
            product.setCreatedByUser(currentUser);
        }
        product.setModifiedByUser(currentUser);
    }

    private User currentUser() {
        AppUserPrincipal principal = currentPrincipal();
        if (principal == null) {
            return null;
        }
        return userRepository.findById(principal.getId()).orElse(null);
    }

    private AppUserPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
