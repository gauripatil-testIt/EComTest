package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductImageUpdateRequest;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.service.ProductImageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.nio.file.Path;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;

    public ProductImageController(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(@PathVariable Long productId,
                                                        @RequestParam("file") MultipartFile file) {
        ProductImage image = productImageService.upload(productId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductImageResponse.from(image, productId));
    }

    @PatchMapping("/{imageId}")
    public ProductImageResponse update(@PathVariable Long productId,
                                        @PathVariable Long imageId,
                                        @RequestBody ProductImageUpdateRequest request) {
        ProductImage image = productImageService.setMainAndOrder(productId, imageId, request);
        return ProductImageResponse.from(image, productId);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{imageId}/{size}")
    public ResponseEntity<Resource> serve(@PathVariable Long productId,
                                           @PathVariable Long imageId,
                                           @PathVariable String size) {
        Path file = productImageService.resolveFile(productId, imageId, size);
        Resource resource = new FileSystemResource(file);
        MediaType mediaType = mediaTypeFor(file.toString());
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }

    private MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }
}
