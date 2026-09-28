package com.ecomtest.service;

import com.ecomtest.entity.ImageStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ImageProcessingException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ImageContentValidator imageContentValidator;
    private final LocalImageStorageService storageService;
    private final ImageResizingService imageResizingService;
    private final ProductImageService self;

    public ProductImageService(ProductImageRepository productImageRepository,
                                ProductRepository productRepository,
                                ImageContentValidator imageContentValidator,
                                LocalImageStorageService storageService,
                                ImageResizingService imageResizingService,
                                @Lazy ProductImageService self) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.imageContentValidator = imageContentValidator;
        this.storageService = storageService;
        this.imageResizingService = imageResizingService;
        this.self = self;
    }

    public ProductImage upload(Long productId, MultipartFile file, int displayOrder, boolean isPrimary) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        String contentType = imageContentValidator.validate(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ImageProcessingException("Unable to read uploaded file");
        }

        if (isPrimary) {
            unsetExistingPrimary(productId);
        }

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setContentType(contentType);
        image.setDisplayOrder(displayOrder);
        image.setPrimary(isPrimary);
        image.setStatus(ImageStatus.PROCESSING);
        image = productImageRepository.save(image);

        self.processAsync(image.getId(), content, contentType);

        return image;
    }

    @Async("imageProcessingExecutor")
    public void processAsync(Long imageId, byte[] content, String contentType) {
        ProductImage image = productImageRepository.findById(imageId).orElse(null);
        if (image == null) {
            return;
        }
        try {
            ImageResizingService.ProductImageAssetKeys assetKeys = imageResizingService.generateDerivedAssets(
                    image.getProduct().getId(), image.getId(), content, contentType);
            image.setThumbnailKey(assetKeys.thumbnailKey());
            image.setMediumKey(assetKeys.mediumKey());
            image.setOriginalKey(assetKeys.originalKey());
            image.setStatus(ImageStatus.READY);
            productImageRepository.save(image);
        } catch (Exception e) {
            image.setStatus(ImageStatus.FAILED);
            productImageRepository.save(image);
        }
    }

    public void delete(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .filter(img -> img.getProduct().getId().equals(productId))
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));

        deleteIfPresent(image.getOriginalKey());
        deleteIfPresent(image.getThumbnailKey());
        deleteIfPresent(image.getMediumKey());

        productImageRepository.delete(image);
    }

    public List<ProductImage> listForProduct(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    private void deleteIfPresent(String key) {
        if (key != null) {
            storageService.delete(key);
        }
    }

    private void unsetExistingPrimary(Long productId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (ProductImage image : images) {
            if (image.isPrimary()) {
                image.setPrimary(false);
                productImageRepository.save(image);
            }
        }
    }
}
