package com.ecomtest.service;

import com.ecomtest.dto.ProductImageUpdateRequest;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.DisplayOrderConflictException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ProductImageService {

    private final ImageStorageService imageStorageService;
    private final ProductImageRepository productImageRepository;

    public ProductImageService(ImageStorageService imageStorageService,
                                ProductImageRepository productImageRepository) {
        this.imageStorageService = imageStorageService;
        this.productImageRepository = productImageRepository;
    }

    public ProductImage upload(Long productId, MultipartFile file) {
        return imageStorageService.store(productId, file);
    }

    public ProductImage get(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));
        if (image.getProduct() == null || !image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product image not found: " + imageId);
        }
        return image;
    }

    public ProductImage setMainAndOrder(Long productId, Long imageId, ProductImageUpdateRequest request) {
        ProductImage image = get(productId, imageId);

        if (request.getDisplayOrder() != null && !request.getDisplayOrder().equals(image.getDisplayOrder())) {
            boolean conflict = productImageRepository.existsByProductIdAndDisplayOrder(productId, request.getDisplayOrder());
            if (conflict) {
                throw new DisplayOrderConflictException(
                        "Display order " + request.getDisplayOrder() + " is already used by another image of this product");
            }
            image.setDisplayOrder(request.getDisplayOrder());
        }

        if (request.getIsMain() != null) {
            if (Boolean.TRUE.equals(request.getIsMain())) {
                List<ProductImage> others = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
                for (ProductImage other : others) {
                    if (!other.getId().equals(image.getId()) && Boolean.TRUE.equals(other.getIsMain())) {
                        other.setIsMain(false);
                        productImageRepository.save(other);
                    }
                }
                image.setIsMain(true);
            } else {
                image.setIsMain(false);
            }
        }

        return productImageRepository.save(image);
    }

    public void delete(Long productId, Long imageId) {
        ProductImage image = get(productId, imageId);
        deleteFileIfExists(image.getThumbnailPath());
        deleteFileIfExists(image.getMediumPath());
        deleteFileIfExists(image.getFullPath());
        productImageRepository.delete(image);
    }

    public Path resolveFile(Long productId, Long imageId, String size) {
        ProductImage image = get(productId, imageId);
        String path = switch (size.toLowerCase()) {
            case "thumbnail" -> image.getThumbnailPath();
            case "medium" -> image.getMediumPath();
            case "full" -> image.getFullPath();
            default -> throw new ResourceNotFoundException("Unknown image size: " + size);
        };
        if (path == null) {
            throw new ResourceNotFoundException("Image '" + size + "' is not available yet for image: " + imageId);
        }
        return Paths.get(path);
    }

    private void deleteFileIfExists(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            // Best-effort cleanup; the DB row removal still proceeds.
        }
    }
}
