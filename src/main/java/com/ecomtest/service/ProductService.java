package com.ecomtest.service;

import com.ecomtest.dto.ProductRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.entity.Role;
import com.ecomtest.entity.User;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductRepository;
import com.ecomtest.security.CurrentUserProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CurrentUserProvider currentUserProvider;

    public ProductService(ProductRepository productRepository, CurrentUserProvider currentUserProvider) {
        this.productRepository = productRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public Product create(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public Product get(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER && product.getStatus() != ProductStatus.ACTIVE) {
            throw new AccessDeniedException("Not allowed to access this product");
        }
        return product;
    }

    public List<Product> list() {
        User currentUser = currentUserProvider.getCurrentUser();
        if (currentUser.getRole() == Role.CUSTOMER) {
            return productRepository.findByStatus(ProductStatus.ACTIVE);
        }
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

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());
        product.setStatus(request.getStatus());

        User currentUser = currentUserProvider.getCurrentUser();
        if (product.getId() == null) {
            product.setCreatedBy(currentUser);
        }
        product.setModifiedBy(currentUser);
    }
}
