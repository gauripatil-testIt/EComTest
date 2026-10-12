package com.ecomtest.service;

import com.ecomtest.dto.ImageOrderRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ImageProcessingService imageProcessingService;

    public ProductImageService(ProductImageRepository productImageRepository,
                                ProductRepository productRepository,
                                ImageProcessingService imageProcessingService) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.imageProcessingService = imageProcessingService;
    }

    @Transactional
    public List<ProductImage> upload(Long productId, MultipartFile[] files, Integer mainImageIndex) {
        Product product = getProduct(productId);

        // Validate every file up front. If any file in the batch is invalid, nothing is
        // written to storage and no ProductImage rows are created for this request.
        for (MultipartFile file : files) {
            imageProcessingService.validate(file);
        }

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        int nextOrder = existing.size();

        List<ProductImage> saved = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            ImageProcessingService.StoredImagePaths paths = imageProcessingService.processAndStore(file, productId);

            ProductImage image = new ProductImage();
            image.setProduct(product);
            image.setOriginalFilename(file.getOriginalFilename());
            image.setContentType(file.getContentType());
            image.setThumbnailPath(paths.thumbnailPath());
            image.setMediumPath(paths.mediumPath());
            image.setFullPath(paths.fullPath());
            image.setDisplayOrder(nextOrder + i);
            image.setCreatedAt(Instant.now());
            image.setMainImage(mainImageIndex != null && mainImageIndex == i);

            saved.add(productImageRepository.save(image));
        }

        if (mainImageIndex != null && mainImageIndex >= 0 && mainImageIndex < saved.size()) {
            ProductImage newMain = saved.get(mainImageIndex);
            clearOtherMainFlags(productId, newMain.getId());
        }

        return saved;
    }

    @Transactional
    public List<ProductImage> updateOrder(Long productId, ImageOrderRequest request) {
        getProduct(productId);
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);

        for (ImageOrderRequest.ImageOrderEntry entry : request.getImages()) {
            ProductImage image = images.stream()
                    .filter(img -> img.getId().equals(entry.getImageId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + entry.getImageId()));
            image.setDisplayOrder(entry.getDisplayOrder());
            productImageRepository.save(image);
        }

        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    @Transactional
    public ProductImage markMain(Long productId, Long imageId) {
        getProduct(productId);
        ProductImage image = getImage(productId, imageId);
        image.setMainImage(true);
        productImageRepository.save(image);
        clearOtherMainFlags(productId, imageId);
        return image;
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        getProduct(productId);
        ProductImage image = getImage(productId, imageId);
        imageProcessingService.deleteFiles(image);
        productImageRepository.delete(image);
    }

    public List<ProductImage> list(Long productId) {
        getProduct(productId);
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    private void clearOtherMainFlags(Long productId, Long keepMainImageId) {
        List<ProductImage> images = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        for (ProductImage image : images) {
            if (!image.getId().equals(keepMainImageId) && image.isMainImage()) {
                image.setMainImage(false);
                productImageRepository.save(image);
            }
        }
    }

    private Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    private ProductImage getImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Image not found: " + imageId);
        }
        return image;
    }
}
