package com.ecomtest.service;

import com.ecomtest.exception.InvalidImageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageProcessingService {

    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final float JPEG_QUALITY = 0.85f;

    private static final int THUMBNAIL_MAX_DIMENSION = 150;
    private static final int MEDIUM_MAX_DIMENSION = 600;
    private static final int FULL_MAX_DIMENSION = 2000;

    private final Path storageDir;

    public ImageProcessingService(@Value("${app.product-images.storage-dir}") String storageDir) {
        this.storageDir = Paths.get(storageDir);
        try {
            Files.createDirectories(this.storageDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create product image storage directory: " + storageDir, e);
        }
    }

    public BufferedImage validateAndDecode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("File is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidImageException("File exceeds the maximum allowed size of 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageException(
                    "Unsupported file type" + (contentType != null ? ": " + contentType : "")
                            + ". Allowed types are JPEG, PNG and WebP");
        }

        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(file.getBytes()));
        } catch (IOException e) {
            throw new InvalidImageException("File is corrupted and could not be read as an image");
        }
        if (image == null) {
            throw new InvalidImageException("File is not a genuine image");
        }
        return image;
    }

    public ImageRenditionPaths generateRenditions(BufferedImage image, String originalFilename) {
        String baseName = UUID.randomUUID().toString();
        try {
            String thumbnailPath = writeRendition(image, baseName, "thumbnail", THUMBNAIL_MAX_DIMENSION);
            String mediumPath = writeRendition(image, baseName, "medium", MEDIUM_MAX_DIMENSION);
            String fullPath = writeRendition(image, baseName, "full", FULL_MAX_DIMENSION);
            return new ImageRenditionPaths(thumbnailPath, mediumPath, fullPath);
        } catch (IOException e) {
            throw new InvalidImageException("Failed to process image: " + originalFilename);
        }
    }

    public void deleteRendition(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(storageDir.resolve(relativePath));
        } catch (IOException e) {
            // best-effort cleanup of a derived file; nothing more we can do here
        }
    }

    private String writeRendition(BufferedImage source, String baseName, String suffix, int maxDimension) throws IOException {
        BufferedImage resized = resizeToFit(source, maxDimension);

        BufferedImage rgbImage = new BufferedImage(resized.getWidth(), resized.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.drawImage(resized, 0, 0, null);
        g.dispose();

        String filename = baseName + "-" + suffix + ".jpg";
        Path target = storageDir.resolve(filename);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(JPEG_QUALITY);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(target.toFile())) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgbImage, null, null), writeParam);
            }
        } finally {
            writer.dispose();
        }

        return filename;
    }

    private BufferedImage resizeToFit(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();

        if (width <= maxDimension && height <= maxDimension) {
            return source;
        }

        double scale = Math.min((double) maxDimension / width, (double) maxDimension / height);
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, newWidth, newHeight, null);
        g.dispose();
        return resized;
    }

    public static class ImageRenditionPaths {
        private final String thumbnailPath;
        private final String mediumPath;
        private final String fullPath;

        public ImageRenditionPaths(String thumbnailPath, String mediumPath, String fullPath) {
            this.thumbnailPath = thumbnailPath;
            this.mediumPath = mediumPath;
            this.fullPath = fullPath;
        }

        public String getThumbnailPath() {
            return thumbnailPath;
        }

        public String getMediumPath() {
            return mediumPath;
        }

        public String getFullPath() {
            return fullPath;
        }
    }
}
