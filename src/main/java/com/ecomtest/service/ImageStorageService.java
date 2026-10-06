package com.ecomtest.service;

import com.ecomtest.entity.ProcessingStatus;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.InvalidImageFileException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class ImageStorageService {

    private final ImageValidationService imageValidationService;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ImageProcessingService imageProcessingService;
    private final String storageRoot;

    public ImageStorageService(ImageValidationService imageValidationService,
                                ProductRepository productRepository,
                                ProductImageRepository productImageRepository,
                                ImageProcessingService imageProcessingService,
                                @Value("${app.images.storage-root:./uploads/products}") String storageRoot) {
        this.imageValidationService = imageValidationService;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.imageProcessingService = imageProcessingService;
        this.storageRoot = storageRoot;
    }

    public ProductImage store(Long productId, MultipartFile file) {
        imageValidationService.validate(file);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        List<ProductImage> existing = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
        int nextDisplayOrder = existing.stream()
                .mapToInt(ProductImage::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
        boolean isFirstImage = existing.isEmpty();

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setDisplayOrder(nextDisplayOrder);
        image.setIsMain(isFirstImage);
        image.setOriginalFilename(file.getOriginalFilename());
        image.setContentType(file.getContentType());
        image.setStatus(ProcessingStatus.PENDING);
        image = productImageRepository.save(image);

        Path imageDir = Paths.get(storageRoot, String.valueOf(productId), String.valueOf(image.getId()));
        try {
            Files.createDirectories(imageDir);
            String extension = extensionFor(file.getContentType());
            Path originalPath = imageDir.resolve("original" + extension);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, originalPath, StandardCopyOption.REPLACE_EXISTING);
            }

            imageProcessingService.generateVariants(image.getId(), originalPath, imageDir);
        } catch (IOException e) {
            image.setStatus(ProcessingStatus.FAILED);
            productImageRepository.save(image);
            throw new InvalidImageFileException("Failed to store the uploaded image");
        }

        return image;
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }
}
