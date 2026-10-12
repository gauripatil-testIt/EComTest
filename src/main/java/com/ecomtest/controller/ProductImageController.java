package com.ecomtest.controller;

import com.ecomtest.dto.ImageOrderRequest;
import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<List<ProductImageResponse>> upload(
            @PathVariable Long productId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "mainImageIndex", required = false) Integer mainImageIndex) {
        List<ProductImage> images = productImageService.upload(productId, files, mainImageIndex);
        List<ProductImageResponse> response = images.stream().map(ProductImageResponse::from).toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return productImageService.list(productId).stream().map(ProductImageResponse::from).toList();
    }

    @PatchMapping("/order")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public List<ProductImageResponse> updateOrder(@PathVariable Long productId, @Valid @RequestBody ImageOrderRequest request) {
        return productImageService.updateOrder(productId, request).stream().map(ProductImageResponse::from).toList();
    }

    @PatchMapping("/{imageId}/main")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductImageResponse markMain(@PathVariable Long productId, @PathVariable Long imageId) {
        return ProductImageResponse.from(productImageService.markMain(productId, imageId));
    }

    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
