package com.ecomtest.service;

import com.ecomtest.dto.ProductImageUpdateRequest;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
@Transactional
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageProcessor imageProcessor;

    public ProductImageService(ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            ImageProcessor imageProcessor) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageProcessor = imageProcessor;
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductImage upload(Long productId, MultipartFile file) {
        Product product = getProduct(productId);

        ImageProcessor.ProcessedImage processed = imageProcessor.process(productId, file);

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setSortOrder(product.getImages().size());
        image.setMain(product.getImages().isEmpty());
        image.setOriginalFilename(processed.getOriginalFilename());
        image.setContentType(processed.getContentType());
        image.setFileSize(processed.getFileSize());
        image.setThumbnailPath(processed.getThumbnailPath());
        image.setMediumPath(processed.getMediumPath());
        image.setFullPath(processed.getFullPath());

        return productImageRepository.save(image);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> list(Long productId) {
        Product product = getProduct(productId);
        return product.getImages();
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ProductImage update(Long productId, Long imageId, ProductImageUpdateRequest request) {
        ProductImage image = getImage(productId, imageId);

        if (Boolean.TRUE.equals(request.getIsMain())) {
            for (ProductImage other : image.getProduct().getImages()) {
                if (!other.getId().equals(image.getId()) && other.isMain()) {
                    other.setMain(false);
                }
            }
            image.setMain(true);
        } else {
            image.setMain(false);
        }

        image.setSortOrder(request.getSortOrder());

        return productImageRepository.save(image);
    }

    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public void delete(Long productId, Long imageId) {
        ProductImage image = getImage(productId, imageId);

        deleteFileQuietly(image.getThumbnailPath());
        deleteFileQuietly(image.getMediumPath());
        deleteFileQuietly(image.getFullPath());

        productImageRepository.delete(image);
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

    private void deleteFileQuietly(String path) {
        try {
            Path target = Paths.get(path);
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // best effort cleanup; nothing more we can do here
        }
    }
}
