package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductRequest;
import com.ecomtest.dto.ProductResponse;
import com.ecomtest.entity.Product;
import com.ecomtest.service.ProductImageService;
import com.ecomtest.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductImageService productImageService;

    public ProductController(ProductService productService, ProductImageService productImageService) {
        this.productService = productService;
        this.productImageService = productImageService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        Product product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(product));
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        Product product = productService.get(id);
        List<ProductImageResponse> images = productImageService.listForProduct(id).stream()
                .map(img -> ProductImageResponse.from(img, null))
                .toList();
        return ProductResponse.from(product, images);
    }

    @GetMapping
    public List<ProductResponse> list() {
        return productService.list().stream().map(ProductResponse::from).toList();
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
