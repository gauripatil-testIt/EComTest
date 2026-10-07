package com.ecomtest.service;

import com.ecomtest.dto.ProductImageMetadataRequest;
import com.ecomtest.dto.ProductImageResponse;
import com.ecomtest.dto.ProductImageUploadResult;
import com.ecomtest.entity.Product;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.entity.ProductStatus;
import com.ecomtest.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ProductImageServiceTest {

    @Autowired
    private ProductImageService productImageService;

    @Autowired
    private ProductRepository productRepository;

    @Value("${app.product-images.storage-dir}")
    private String storageDir;

    private byte[] genuineJpegBytes(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private Long createProduct(String sku) {
        Product product = new Product();
        product.setName("Test Product");
        product.setSku(sku);
        product.setPrice(new BigDecimal("9.99"));
        product.setStock(10);
        product.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(product).getId();
    }

    @Test
    void settingNewMainImageUnsetsThePreviousOne() throws Exception {
        Long productId = createProduct("SKU-SVC-MAIN-1");

        MultipartFile firstFile = new MockMultipartFile(
                "files", "one.jpg", "image/jpeg", genuineJpegBytes(100, 100, Color.BLUE));
        ProductImageMetadataRequest firstMeta = new ProductImageMetadataRequest();
        firstMeta.setFilename("one.jpg");
        firstMeta.setDisplayOrder(0);
        firstMeta.setIsMain(true);

        ProductImageUploadResult firstResult =
                productImageService.uploadImages(productId, List.of(firstFile), List.of(firstMeta));
        assertEquals(1, firstResult.getUploaded().size());
        assertTrue(firstResult.getUploaded().get(0).getIsMain());

        MultipartFile secondFile = new MockMultipartFile(
                "files", "two.jpg", "image/jpeg", genuineJpegBytes(100, 100, Color.GREEN));
        ProductImageMetadataRequest secondMeta = new ProductImageMetadataRequest();
        secondMeta.setFilename("two.jpg");
        secondMeta.setDisplayOrder(1);
        secondMeta.setIsMain(true);

        ProductImageUploadResult secondResult =
                productImageService.uploadImages(productId, List.of(secondFile), List.of(secondMeta));
        assertEquals(1, secondResult.getUploaded().size());
        assertTrue(secondResult.getUploaded().get(0).getIsMain());

        List<ProductImage> images = productImageService.listImages(productId);
        long mainCount = images.stream().filter(ProductImage::getIsMain).count();
        assertEquals(1, mainCount);
        assertTrue(images.stream()
                .filter(img -> "two.jpg".equals(img.getOriginalFilename()))
                .findFirst()
                .orElseThrow()
                .getIsMain());
        assertFalse(images.stream()
                .filter(img -> "one.jpg".equals(img.getOriginalFilename()))
                .findFirst()
                .orElseThrow()
                .getIsMain());
    }

    @Test
    void uploadingInvalidFileAlongsideValidOneKeepsTheValidOne() throws Exception {
        Long productId = createProduct("SKU-SVC-PARTIAL-1");

        MultipartFile validFile = new MockMultipartFile(
                "files", "valid.jpg", "image/jpeg", genuineJpegBytes(80, 80, Color.RED));
        MultipartFile invalidFile = new MockMultipartFile(
                "files", "invalid.jpg", "image/jpeg", "not an image".getBytes());

        ProductImageUploadResult result =
                productImageService.uploadImages(productId, List.of(validFile, invalidFile), List.of());

        assertEquals(1, result.getUploaded().size());
        assertEquals(1, result.getErrors().size());
        assertEquals("invalid.jpg", result.getErrors().get(0).getFilename());
    }

    @Test
    void deletingImageRemovesDbRowAndAllRenditionFiles() throws Exception {
        Long productId = createProduct("SKU-SVC-DELETE-1");

        MultipartFile file = new MockMultipartFile(
                "files", "to-delete.jpg", "image/jpeg", genuineJpegBytes(90, 90, Color.MAGENTA));

        ProductImageUploadResult result =
                productImageService.uploadImages(productId, List.of(file), List.of());
        assertEquals(1, result.getUploaded().size());
        ProductImageResponse uploaded = result.getUploaded().get(0);

        ProductImage persisted = productImageService.listImages(productId).stream()
                .filter(img -> img.getId().equals(uploaded.getId()))
                .findFirst()
                .orElseThrow();

        Path storageBase = Paths.get(storageDir);
        Path thumbnailPath = storageBase.resolve(persisted.getThumbnailPath());
        Path mediumPath = storageBase.resolve(persisted.getMediumPath());
        Path fullPath = storageBase.resolve(persisted.getFullPath());

        assertTrue(Files.exists(thumbnailPath));
        assertTrue(Files.exists(mediumPath));
        assertTrue(Files.exists(fullPath));

        productImageService.deleteImage(productId, persisted.getId());

        assertFalse(Files.exists(thumbnailPath));
        assertFalse(Files.exists(mediumPath));
        assertFalse(Files.exists(fullPath));

        assertTrue(productImageService.listImages(productId).stream()
                .noneMatch(img -> img.getId().equals(persisted.getId())));
    }
}
