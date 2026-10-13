package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductImageUpdateRequest;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.ProductImageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/products/{id}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ProductImageResponse> upload(@PathVariable("id") Long productId,
            @RequestParam("file") MultipartFile file) {
        ProductImage image = productImageService.upload(productId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductImageResponse.from(image));
    }

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable("id") Long productId) {
        return productImageService.list(productId).stream().map(ProductImageResponse::from).toList();
    }

    @PatchMapping("/{imageId}")
    public ProductImageResponse update(@PathVariable("id") Long productId,
            @PathVariable("imageId") Long imageId,
            @Valid @RequestBody ProductImageUpdateRequest request) {
        return ProductImageResponse.from(productImageService.update(productId, imageId, request));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long productId,
            @PathVariable("imageId") Long imageId) {
        productImageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
