package com.ecomtest.service;

import com.ecomtest.exception.ImageProcessingException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates the derived assets (thumbnail, medium, capped original) for a product image
 * and stores them via {@link LocalImageStorageService}.
 */
@Service
public class ImageResizingService {

    private static final int THUMBNAIL_SIZE = 150;
    private static final int MEDIUM_SIZE = 600;
    private static final int MAX_ORIGINAL_DIMENSION = 2000;

    private static final String DERIVED_FORMAT = "png";

    private final LocalImageStorageService storageService;

    public ImageResizingService(LocalImageStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Decodes the given original image bytes and produces/stores three derived assets:
     * a 150x150 thumbnail, a 600x600 medium, and an original capped at 2000px on its
     * longest side. If any step fails, any assets already written during this call are
     * removed before the exception is propagated, so no partial derived files are left behind.
     *
     * @throws ImageProcessingException if the image cannot be decoded or processing fails
     */
    public ProductImageAssetKeys generateDerivedAssets(Long productId, Long imageId, byte[] originalBytes, String contentType) {
        List<String> writtenKeys = new ArrayList<>();
        try {
            BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(originalBytes));
            if (sourceImage == null) {
                throw new ImageProcessingException("Unable to decode image content");
            }

            String thumbnailKey = resizeAndStore(sourceImage, THUMBNAIL_SIZE, THUMBNAIL_SIZE, productId, imageId, "thumbnail");
            writtenKeys.add(thumbnailKey);

            String mediumKey = resizeAndStore(sourceImage, MEDIUM_SIZE, MEDIUM_SIZE, productId, imageId, "medium");
            writtenKeys.add(mediumKey);

            String originalKey = storeCappedOriginal(sourceImage, originalBytes, contentType, productId, imageId);
            writtenKeys.add(originalKey);

            return new ProductImageAssetKeys(thumbnailKey, mediumKey, originalKey);
        } catch (ImageProcessingException e) {
            cleanUp(writtenKeys);
            throw e;
        } catch (IOException e) {
            cleanUp(writtenKeys);
            throw new ImageProcessingException("Failed to process image", e);
        } catch (RuntimeException e) {
            cleanUp(writtenKeys);
            throw new ImageProcessingException("Failed to process image", e);
        }
    }

    private String resizeAndStore(BufferedImage sourceImage, int width, int height, Long productId, Long imageId, String label) throws IOException {
        BufferedImage resized = Thumbnails.of(sourceImage)
                .size(width, height)
                .asBufferedImage();
        byte[] bytes = toBytes(resized);
        return storageService.store(productId, imageId, label + "." + DERIVED_FORMAT, bytes);
    }

    private String storeCappedOriginal(BufferedImage sourceImage, byte[] originalBytes, String contentType, Long productId, Long imageId) throws IOException {
        int longestSide = Math.max(sourceImage.getWidth(), sourceImage.getHeight());
        String extension = extensionFor(contentType);

        if (longestSide <= MAX_ORIGINAL_DIMENSION) {
            return storageService.store(productId, imageId, "original." + extension, originalBytes);
        }

        BufferedImage capped = Thumbnails.of(sourceImage)
                .size(MAX_ORIGINAL_DIMENSION, MAX_ORIGINAL_DIMENSION)
                .asBufferedImage();
        byte[] bytes = toBytes(capped);
        return storageService.store(productId, imageId, "original." + DERIVED_FORMAT, bytes);
    }

    private byte[] toBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, DERIVED_FORMAT, outputStream);
        return outputStream.toByteArray();
    }

    private String extensionFor(String contentType) {
        if (contentType == null) {
            return DERIVED_FORMAT;
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> DERIVED_FORMAT;
        };
    }

    private void cleanUp(List<String> keys) {
        for (String key : keys) {
            try {
                storageService.delete(key);
            } catch (RuntimeException ignored) {
                // best-effort cleanup; nothing further to do if deletion of a partial file fails
            }
        }
    }

    /**
     * Relative storage keys for the three derived assets produced for a single product image.
     */
    public record ProductImageAssetKeys(String thumbnailKey, String mediumKey, String originalKey) {
    }
}
