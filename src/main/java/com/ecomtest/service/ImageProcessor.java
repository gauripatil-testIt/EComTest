package com.ecomtest.service;

import com.ecomtest.exception.ImageUploadException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates and resizes uploaded product images, persisting thumbnail, medium
 * and full-size variants to the local filesystem under ./uploads/products.
 */
@Component
public class ImageProcessor {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int THUMBNAIL_WIDTH = 150;
    private static final int MEDIUM_WIDTH = 600;
    private static final int FULL_MAX_DIMENSION = 2000;
    private static final String UPLOAD_ROOT = "./uploads/products";

    public ProcessedImage process(Long productId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageUploadException("The uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageUploadException("The uploaded file is too large; maximum size is 5MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ImageUploadException("The uploaded file could not be read");
        }

        if (content.length > MAX_FILE_SIZE) {
            throw new ImageUploadException("The uploaded file is too large; maximum size is 5MB");
        }

        String detectedType = detectImageType(content);
        if (detectedType == null) {
            throw new ImageUploadException(
                    "The uploaded file is not a supported image type; only JPEG, PNG and WebP are accepted");
        }

        BufferedImage original;
        try (InputStream in = new ByteArrayInputStream(content)) {
            original = ImageIO.read(in);
        } catch (IOException e) {
            throw new ImageUploadException("The uploaded image is corrupted and could not be read");
        }
        if (original == null) {
            throw new ImageUploadException("The uploaded image is corrupted and could not be read");
        }

        Path productDir = Paths.get(UPLOAD_ROOT, String.valueOf(productId));
        List<Path> writtenFiles = new ArrayList<>();
        try {
            Files.createDirectories(productDir);

            String baseName = UUID.randomUUID().toString();

            Path thumbnailPath = writeResized(original, productDir, baseName + "_thumbnail.jpg",
                    THUMBNAIL_WIDTH, writtenFiles);
            Path mediumPath = writeResized(original, productDir, baseName + "_medium.jpg",
                    MEDIUM_WIDTH, writtenFiles);
            Path fullPath = writeFull(original, productDir, baseName + "_full.jpg", writtenFiles);

            ProcessedImage processed = new ProcessedImage();
            processed.setOriginalFilename(file.getOriginalFilename());
            processed.setContentType(detectedType);
            processed.setFileSize(content.length);
            processed.setThumbnailPath(thumbnailPath.toString());
            processed.setMediumPath(mediumPath.toString());
            processed.setFullPath(fullPath.toString());
            return processed;
        } catch (ImageUploadException e) {
            cleanup(writtenFiles);
            throw e;
        } catch (IOException e) {
            cleanup(writtenFiles);
            throw new ImageUploadException("The uploaded image could not be processed");
        }
    }

    private Path writeResized(BufferedImage original, Path dir, String fileName, int maxWidth,
            List<Path> writtenFiles) throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();

        double scale = Math.min(1.0, (double) maxWidth / width);

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        return writeScaled(original, dir, fileName, targetWidth, targetHeight, writtenFiles);
    }

    private Path writeFull(BufferedImage original, Path dir, String fileName, List<Path> writtenFiles)
            throws IOException {
        int width = original.getWidth();
        int height = original.getHeight();
        int longestSide = Math.max(width, height);

        double scale = Math.min(1.0, (double) FULL_MAX_DIMENSION / longestSide);

        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        return writeScaled(original, dir, fileName, targetWidth, targetHeight, writtenFiles);
    }

    private Path writeScaled(BufferedImage original, Path dir, String fileName, int targetWidth,
            int targetHeight, List<Path> writtenFiles) throws IOException {
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, targetWidth, targetHeight);
            g.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        Path target = dir.resolve(fileName);
        Path temp = Files.createTempFile(dir, "upload-", ".tmp");
        writtenFiles.add(temp);

        if (!ImageIO.write(scaled, "jpg", temp.toFile())) {
            throw new ImageUploadException("The uploaded image could not be processed");
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        writtenFiles.remove(temp);
        writtenFiles.add(target);
        return target;
    }

    private void cleanup(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // best effort cleanup; nothing more we can do here
            }
        }
    }

    private String detectImageType(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89
                && content[1] == 0x50
                && content[2] == 0x4E
                && content[3] == 0x47
                && content[4] == 0x0D
                && content[5] == 0x0A
                && content[6] == 0x1A
                && content[7] == 0x0A) {
            return "image/png";
        }
        if (content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public static class ProcessedImage {
        private String originalFilename;
        private String contentType;
        private long fileSize;
        private String thumbnailPath;
        private String mediumPath;
        private String fullPath;

        public String getOriginalFilename() {
            return originalFilename;
        }

        public void setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public String getThumbnailPath() {
            return thumbnailPath;
        }

        public void setThumbnailPath(String thumbnailPath) {
            this.thumbnailPath = thumbnailPath;
        }

        public String getMediumPath() {
            return mediumPath;
        }

        public void setMediumPath(String mediumPath) {
            this.mediumPath = mediumPath;
        }

        public String getFullPath() {
            return fullPath;
        }

        public void setFullPath(String fullPath) {
            this.fullPath = fullPath;
        }
    }
}
