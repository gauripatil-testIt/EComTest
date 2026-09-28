package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.LocalImageStorageService;
import com.ecomtest.service.ProductImageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ProductImageController {

    private final ProductImageService productImageService;
    private final LocalImageStorageService storageService;

    public ProductImageController(ProductImageService productImageService, LocalImageStorageService storageService) {
        this.productImageService = productImageService;
        this.storageService = storageService;
    }

    @PostMapping(value = "/api/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(@PathVariable Long id,
                                                        @RequestPart("file") MultipartFile file,
                                                        @RequestParam("displayOrder") int displayOrder,
                                                        @RequestParam("isPrimary") boolean isPrimary) {
        ProductImage image = productImageService.upload(id, file, displayOrder, isPrimary);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductImageResponse.from(image, storageService));
    }

    @DeleteMapping("/api/products/{id}/images/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.delete(id, imageId);
        return ResponseEntity.noContent().build();
    }
}
