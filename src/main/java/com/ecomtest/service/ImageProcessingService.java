package com.ecomtest.service;

import com.ecomtest.entity.ProcessingStatus;
import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ResourceNotFoundException;
import com.ecomtest.repository.ProductImageRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Service
public class ImageProcessingService {

    private final ProductImageRepository productImageRepository;

    public ImageProcessingService(ProductImageRepository productImageRepository) {
        this.productImageRepository = productImageRepository;
    }

    @Async("imageProcessingExecutor")
    public void generateVariants(Long imageId, Path originalPath, Path imageDir) {
        ProductImage image = productImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image not found: " + imageId));

        try {
            BufferedImage original = ImageIO.read(originalPath.toFile());
            if (original == null) {
                throw new IOException("Unable to decode stored image");
            }

            String formatName = formatNameFor(image.getContentType());

            Path thumbnailPath = imageDir.resolve("thumbnail" + extensionFor(formatName));
            writeResized(original, 150, 150, formatName, thumbnailPath.toFile());

            Path mediumPath = imageDir.resolve("medium" + extensionFor(formatName));
            writeResized(original, 600, Integer.MAX_VALUE, formatName, mediumPath.toFile());

            Path fullPath = imageDir.resolve("full" + extensionFor(formatName));
            writeResized(original, 2000, 2000, formatName, fullPath.toFile());

            image.setThumbnailPath(thumbnailPath.toString());
            image.setMediumPath(mediumPath.toString());
            image.setFullPath(fullPath.toString());
            image.setStatus(ProcessingStatus.READY);
        } catch (IOException e) {
            image.setStatus(ProcessingStatus.FAILED);
        }

        productImageRepository.save(image);
    }

    private void writeResized(BufferedImage original, int maxWidth, int maxHeight, String formatName, File target)
            throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();

        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        scale = Math.min(scale, 1.0);

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(original.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH), 0, 0, null);
        graphics.dispose();

        if (!ImageIO.write(resized, formatName, target)) {
            throw new IOException("No writer available for format " + formatName);
        }
    }

    private String formatNameFor(String contentType) {
        if (contentType == null) {
            return "jpg";
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }

    private String extensionFor(String formatName) {
        return "." + formatName;
    }
}
