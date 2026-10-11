package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.ProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ProductImageResponse> upload(@PathVariable Long productId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "displayOrder", required = false) Integer displayOrder,
                                                         @RequestParam(value = "isMain", required = false) Boolean isMain) {
        ProductImage image = productImageService.upload(productId, file, displayOrder, isMain);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductImageResponse.from(image));
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return productImageService.list(productId).stream().map(ProductImageResponse::from).toList();
    }
}
