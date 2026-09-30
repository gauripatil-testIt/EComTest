package com.ecomtest.service;

import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.InvalidImageException;
import com.ecomtest.exception.ImageTooLargeException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates validation (ImageProcessingService), storage (ImageStorageService) and
 * persistence (ProductImageRepository) for product images, enforcing the single-main-image
 * and sort-order business rules.
 */
@Service
public class ProductImageService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageProcessingService imageProcessingService;
    private final ImageStorageService imageStorageService;

    public ProductImageService(ProductRepository productRepository,
                                ProductImageRepository productImageRepository,
                                ImageProcessingService imageProcessingService,
                                ImageStorageService imageStorageService) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageProcessingService = imageProcessingService;
        this.imageStorageService = imageStorageService;
    }

    public List<ProductImageResponse> uploadImages(Long productId, MultipartFile[] files) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<ProductImage> existing = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
        int nextSortOrder = existing.stream()
                .map(ProductImage::getSortOrder)
                .max(Integer::compareTo)
                .orElse(-1) + 1;
        boolean hasMain = existing.stream().anyMatch(image -> Boolean.TRUE.equals(image.getIsMain()));

        List<ProductImageResponse> responses = new ArrayList<>();
        if (files == null) {
            return responses;
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new ImageTooLargeException("Uploaded file exceeds the maximum allowed size of 5MB: "
                        + file.getOriginalFilename());
            }

            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException e) {
                throw new InvalidImageException("Could not read uploaded file: " + e.getMessage());
            }

            String contentType = imageProcessingService.detectGenuineContentType(bytes);
            ImageVariants variants = imageProcessingService.generateVariants(bytes);

            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setContentType(contentType);
            boolean isMain = !hasMain;
            image.setIsMain(isMain);
            image.setSortOrder(nextSortOrder);
            // Persist first to obtain the generated id used in the stored file names.
            image.setThumbnailPath("");
            image.setMediumPath("");
            image.setFullPath("");
            ProductImage saved = productImageRepository.save(image);

            String thumbnailPath = imageStorageService.store(productId, saved.getId(), "thumbnail",
                    variants.getExtension(), variants.getThumbnail());
            String mediumPath = imageStorageService.store(productId, saved.getId(), "medium",
                    variants.getExtension(), variants.getMedium());
            String fullPath = imageStorageService.store(productId, saved.getId(), "full",
                    variants.getExtension(), variants.getFull());

            saved.setThumbnailPath(thumbnailPath);
            saved.setMediumPath(mediumPath);
            saved.setFullPath(fullPath);
            saved = productImageRepository.save(saved);

            responses.add(ProductImageResponse.from(saved));

            hasMain = hasMain || isMain;
            nextSortOrder++;
        }

        return responses;
    }

    public void deleteImage(Long productId, Long imageId) {
        productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        if (image.getProduct() == null || !productId.equals(image.getProduct().getId())) {
            throw new ResourceNotFoundException("Image not found for product " + productId + ": " + imageId);
        }

        imageStorageService.delete(image.getThumbnailPath());
        imageStorageService.delete(image.getMediumPath());
        imageStorageService.delete(image.getFullPath());

        boolean wasMain = Boolean.TRUE.equals(image.getIsMain());
        productImageRepository.delete(image);

        if (wasMain) {
            productImageRepository.findFirstByProductIdOrderBySortOrderAsc(productId)
                    .ifPresent(next -> {
                        next.setIsMain(true);
                        productImageRepository.save(next);
                    });
        }
    }

    public List<ProductImageResponse> listImages(Long productId) {
        return productImageRepository.findByProductIdOrderBySortOrderAsc(productId).stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    /**
     * Removes every stored image (files and rows) belonging to a product. Used when a product
     * itself is deleted, so no orphaned files remain on disk and the product_id foreign key does
     * not block the product row from being removed.
     */
    public void deleteAllImages(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderBySortOrderAsc(productId);
        for (ProductImage image : images) {
            imageStorageService.delete(image.getThumbnailPath());
            imageStorageService.delete(image.getMediumPath());
            imageStorageService.delete(image.getFullPath());
        }
        productImageRepository.deleteAll(images);
    }
}
