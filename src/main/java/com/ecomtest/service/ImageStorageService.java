package com.ecomtest.service;

import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.ImageErrorCode;
import com.ecomtest.exception.ImageValidationException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int THUMBNAIL_WIDTH = 150;
    private static final int MEDIUM_WIDTH = 600;
    private static final int FULL_WIDTH = 1200;

    private final Path uploadRoot;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir);
    }

    public StoredImagePaths store(Long productId, Long imageId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ImageValidationException(ImageErrorCode.INVALID_IMAGE_FORMAT, "No file was uploaded");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ImageValidationException(ImageErrorCode.INVALID_IMAGE_FORMAT,
                    "Unsupported image type: " + contentType + ". Accepted formats are JPEG, PNG and WebP.");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ImageValidationException(ImageErrorCode.FILE_TOO_LARGE,
                    "Image exceeds the maximum allowed size of 5MB");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ImageValidationException(ImageErrorCode.CORRUPTED_IMAGE, "Failed to read uploaded file");
        }

        BufferedImage original;
        try {
            original = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            original = null;
        }
        if (original == null) {
            throw new ImageValidationException(ImageErrorCode.CORRUPTED_IMAGE,
                    "The uploaded file is not a genuine image or is corrupted");
        }

        Path targetDir = uploadRoot.resolve(String.valueOf(productId)).resolve(String.valueOf(imageId));
        Path thumbnailFile = targetDir.resolve("thumbnail.jpg");
        Path mediumFile = targetDir.resolve("medium.jpg");
        Path fullFile = targetDir.resolve("full.jpg");

        try {
            Files.createDirectories(targetDir);

            Thumbnails.of(original)
                    .width(THUMBNAIL_WIDTH)
                    .outputFormat("jpg")
                    .toFile(thumbnailFile.toFile());

            Thumbnails.of(original)
                    .width(MEDIUM_WIDTH)
                    .outputFormat("jpg")
                    .toFile(mediumFile.toFile());

            int fullWidth = Math.min(original.getWidth(), FULL_WIDTH);
            Thumbnails.of(original)
                    .width(fullWidth)
                    .outputFormat("jpg")
                    .toFile(fullFile.toFile());
        } catch (IOException | RuntimeException e) {
            deleteQuietly(thumbnailFile);
            deleteQuietly(mediumFile);
            deleteQuietly(fullFile);
            deleteDirIfEmpty(targetDir);
            throw new ImageValidationException(ImageErrorCode.CORRUPTED_IMAGE,
                    "Failed to process the uploaded image");
        }

        String relativeThumbnail = relativize(thumbnailFile);
        String relativeMedium = relativize(mediumFile);
        String relativeFull = relativize(fullFile);

        return new StoredImagePaths(relativeThumbnail, relativeMedium, relativeFull);
    }

    public void delete(ProductImage image) {
        deleteQuietly(resolveAbsolute(image.getThumbnailPath()));
        deleteQuietly(resolveAbsolute(image.getMediumPath()));
        deleteQuietly(resolveAbsolute(image.getFullPath()));
    }

    private Path resolveAbsolute(String relativePath) {
        return uploadRoot.resolve(relativePath);
    }

    private String relativize(Path file) {
        return uploadRoot.relativize(file).toString().replace('\\', '/');
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup, nothing more we can do here
        }
    }

    private void deleteDirIfEmpty(Path dir) {
        try {
            Files.deleteIfExists(dir);
        } catch (IOException ignored) {
            // best-effort cleanup, nothing more we can do here
        }
    }

    public static class StoredImagePaths {
        private final String thumbnailPath;
        private final String mediumPath;
        private final String fullPath;

        public StoredImagePaths(String thumbnailPath, String mediumPath, String fullPath) {
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
