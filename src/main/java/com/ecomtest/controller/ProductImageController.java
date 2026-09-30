package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.service.ImageStorageService;
import com.ecomtest.service.ProductImageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class ProductImageController {

    private final ProductImageService productImageService;
    private final ProductImageRepository productImageRepository;
    private final ImageStorageService imageStorageService;

    public ProductImageController(ProductImageService productImageService,
                                   ProductImageRepository productImageRepository,
                                   ImageStorageService imageStorageService) {
        this.productImageService = productImageService;
        this.productImageRepository = productImageRepository;
        this.imageStorageService = imageStorageService;
    }

    @PostMapping("/api/products/{id}/images")
    public ResponseEntity<List<ProductImageResponse>> upload(@PathVariable Long id,
                                                               @RequestParam("files") MultipartFile[] files) {
        List<ProductImageResponse> responses = productImageService.uploadImages(id, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    @DeleteMapping("/api/products/{id}/images/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @PathVariable Long imageId) {
        productImageService.deleteImage(id, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/products/{productId}/images/{imageId}/{size}")
    public ResponseEntity<Resource> serve(@PathVariable Long productId,
                                           @PathVariable Long imageId,
                                           @PathVariable String size) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        if (image.getProduct() == null || !productId.equals(image.getProduct().getId())) {
            throw new ResourceNotFoundException("Image not found for product " + productId + ": " + imageId);
        }

        String path;
        switch (size) {
            case "thumbnail" -> path = image.getThumbnailPath();
            case "medium" -> path = image.getMediumPath();
            case "full" -> path = image.getFullPath();
            default -> throw new ResourceNotFoundException("Unknown image size: " + size);
        }

        Resource resource = imageStorageService.loadAsResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.getContentType()))
                .body(resource);
    }
}
