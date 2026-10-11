package com.ecomtest.service;

import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
public class ProductImageService {

    private final ProductImageRepository productImageRepository;
    private final ProductService productService;
    private final ImageStorageService imageStorageService;

    public ProductImageService(ProductImageRepository productImageRepository,
                                ProductService productService,
                                ImageStorageService imageStorageService) {
        this.productImageRepository = productImageRepository;
        this.productService = productService;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public ProductImage upload(Long productId, MultipartFile file, Integer displayOrder, Boolean isMain) {
        Product product = productService.get(productId);

        boolean hasExistingMain = productImageRepository.existsByProductIdAndMainImageTrue(productId);
        boolean mainImage = Boolean.TRUE.equals(isMain) || !hasExistingMain;

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setOriginalFilename(file.getOriginalFilename());
        image.setContentType(file.getContentType());
        image.setDisplayOrder(displayOrder != null ? displayOrder : nextDisplayOrder(productId));
        image.setMainImage(mainImage);
        image.setCreatedAt(Instant.now());
        // placeholder paths until the file is actually stored, satisfying the not-null columns
        image.setThumbnailPath("");
        image.setMediumPath("");
        image.setFullPath("");

        ProductImage saved = productImageRepository.save(image);

        ImageStorageService.StoredImagePaths paths = imageStorageService.store(productId, saved.getId(), file);

        saved.setThumbnailPath(paths.getThumbnailPath());
        saved.setMediumPath(paths.getMediumPath());
        saved.setFullPath(paths.getFullPath());

        if (mainImage && hasExistingMain) {
            for (ProductImage existing : productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId)) {
                if (!existing.getId().equals(saved.getId()) && existing.isMainImage()) {
                    existing.setMainImage(false);
                    productImageRepository.save(existing);
                }
            }
        }

        return productImageRepository.save(saved);
    }

    public List<ProductImage> list(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId);
    }

    @Transactional
    public void delete(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));

        if (!image.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Product image not found: " + imageId);
        }

        productImageRepository.delete(image);
        imageStorageService.delete(image);
    }

    private int nextDisplayOrder(Long productId) {
        return productImageRepository.findByProductIdOrderByDisplayOrderAsc(productId).size();
    }
}
