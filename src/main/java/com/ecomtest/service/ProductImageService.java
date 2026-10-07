package com.ecomtest.service;

import com.ecomtest.dto.ProductImageMetadataRequest;
import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductImageUploadResult;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.InvalidImageException;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import com.ecomtest.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final ImageProcessingService imageProcessingService;
    private final String baseUrl;

    public ProductImageService(ProductImageRepository productImageRepository,
                                ProductRepository productRepository,
                                ImageProcessingService imageProcessingService,
                                @Value("${app.product-images.base-url}") String baseUrl) {
        this.productImageRepository = productImageRepository;
        this.productRepository = productRepository;
        this.imageProcessingService = imageProcessingService;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public ProductImageUploadResult uploadImages(Long productId, List<MultipartFile> files,
                                                  List<ProductImageMetadataRequest> metadata) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));

        Map<String, ProductImageMetadataRequest> metadataByFilename = metadata == null
                ? Map.of()
                : metadata.stream()
                        .filter(m -> m.getFilename() != null)
                        .collect(Collectors.toMap(ProductImageMetadataRequest::getFilename, m -> m, (a, b) -> a));

        ProductImageUploadResult result = new ProductImageUploadResult();
        int nextDisplayOrder = productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).size();

        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            try {
                ProductImageMetadataRequest fileMetadata = originalFilename != null
                        ? metadataByFilename.get(originalFilename)
                        : null;

                BufferedImage decoded = imageProcessingService.validateAndDecode(file);
                ImageProcessingService.ImageRenditionPaths renditions =
                        imageProcessingService.generateRenditions(decoded, originalFilename);

                ProductImage image = new ProductImage();
                image.setProduct(product);
                image.setOriginalFilename(originalFilename);
                image.setContentType(file.getContentType());
                image.setThumbnailPath(renditions.getThumbnailPath());
                image.setMediumPath(renditions.getMediumPath());
                image.setFullPath(renditions.getFullPath());
                image.setCreatedAt(Instant.now());

                Integer displayOrder = fileMetadata != null ? fileMetadata.getDisplayOrder() : null;
                image.setDisplayOrder(displayOrder != null ? displayOrder : nextDisplayOrder);
                nextDisplayOrder++;

                boolean isMain = fileMetadata != null && Boolean.TRUE.equals(fileMetadata.getIsMain());
                image.setIsMain(isMain);

                if (isMain) {
                    unsetCurrentMain(productId);
                }

                ProductImage saved = productImageRepository.save(image);
                result.addUploaded(ProductImageResponse.from(saved, baseUrl));
            } catch (InvalidImageException e) {
                result.addError(originalFilename, e.getMessage());
            }
        }

        return result;
    }

    public List<ProductImage> listImages(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    public List<ProductImageResponse> listImageResponses(Long productId) {
        return listImages(productId).stream()
                .map(image -> ProductImageResponse.from(image, baseUrl))
                .toList();
    }

    @Transactional
    public ProductImageResponse setMainImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));
        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product image not found: " + imageId);
        }

        unsetCurrentMain(productId);
        image.setIsMain(true);
        ProductImage saved = productImageRepository.save(image);
        return ProductImageResponse.from(saved, baseUrl);
    }

    @Transactional
    public void deleteImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));
        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product image not found: " + imageId);
        }

        imageProcessingService.deleteRendition(image.getThumbnailPath());
        imageProcessingService.deleteRendition(image.getMediumPath());
        imageProcessingService.deleteRendition(image.getFullPath());

        productImageRepository.delete(image);
    }

    private void unsetCurrentMain(Long productId) {
        productImageRepository.findByProductIdAndIsMainTrue(productId).ifPresent(current -> {
            current.setIsMain(false);
            productImageRepository.save(current);
        });
    }
}
