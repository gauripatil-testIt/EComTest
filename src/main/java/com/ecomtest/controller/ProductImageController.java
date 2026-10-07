package com.ecomtest.controller;

import com.ecomtest.dto.ProductImageMetadataRequest;
import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductImageUploadResult;
import com.ecomtest.exception.InvalidImageException;
import com.ecomtest.service.ProductImageService;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageService productImageService;
    private final ObjectMapper objectMapper;

    public ProductImageController(ProductImageService productImageService, ObjectMapper objectMapper) {
        this.productImageService = productImageService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageUploadResult> upload(
            @PathVariable Long productId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "metadata", required = false) String metadataJson) {
        List<ProductImageMetadataRequest> metadata = parseMetadata(metadataJson);
        ProductImageUploadResult result = productImageService.uploadImages(productId, files, metadata);
        HttpStatus status = result.getUploaded().isEmpty() ? HttpStatus.BAD_REQUEST : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return productImageService.listImageResponses(productId);
    }

    @PatchMapping("/{imageId}/main")
    public ProductImageResponse setMain(@PathVariable Long productId, @PathVariable Long imageId) {
        return productImageService.setMainImage(productId, imageId);
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long productId, @PathVariable Long imageId) {
        productImageService.deleteImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    private List<ProductImageMetadataRequest> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            ProductImageMetadataRequest[] parsed =
                    objectMapper.readValue(metadataJson, ProductImageMetadataRequest[].class);
            return Arrays.asList(parsed);
        } catch (RuntimeException e) {
            throw new InvalidImageException("Invalid metadata JSON: " + e.getMessage());
        }
    }
}
