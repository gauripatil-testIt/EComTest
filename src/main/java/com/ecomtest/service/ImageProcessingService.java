package com.ecomtest.service;

import com.ecomtest.entity.ProductImage;
import com.ecomtest.exception.InvalidImageException;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

@Service
public class ImageProcessingService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int THUMBNAIL_SIZE = 150;
    private static final int MEDIUM_SIZE = 500;
    private static final int FULL_SIZE_CAP = 2000;

    private final String storageLocation;

    public ImageProcessingService(@Value("${app.images.storage-location}") String storageLocation) {
        this.storageLocation = storageLocation;
    }

    public record StoredImagePaths(String thumbnailPath, String mediumPath, String fullPath) {
    }

    public void validate(MultipartFile file) {
        detectAndValidateFormat(file);
    }

    public StoredImagePaths processAndStore(MultipartFile file, Long productId) {
        String extension = detectAndValidateFormat(file);

        try {
            byte[] bytes = file.getBytes();

            BufferedImage orientedImage;
            try (ByteArrayInputStream decodeStream = new ByteArrayInputStream(bytes)) {
                orientedImage = Thumbnails.of(decodeStream)
                        .scale(1.0)
                        .asBufferedImage();
            } catch (IOException e) {
                throw new InvalidImageException("The uploaded file is corrupted and could not be processed");
            }

            // Apply EXIF auto-rotation so non-upright orientations are corrected.
            orientedImage = applyExifAutoRotation(bytes, orientedImage);

            Path productDir = Path.of(storageLocation, String.valueOf(productId));
            Files.createDirectories(productDir);

            String baseName = UUID.randomUUID().toString();
            String thumbnailRelativePath = productId + "/" + baseName + "-thumbnail." + extension;
            String mediumRelativePath = productId + "/" + baseName + "-medium." + extension;
            String fullRelativePath = productId + "/" + baseName + "-full." + extension;

            File thumbnailFile = Path.of(storageLocation, thumbnailRelativePath).toFile();
            File mediumFile = Path.of(storageLocation, mediumRelativePath).toFile();
            File fullFile = Path.of(storageLocation, fullRelativePath).toFile();

            Thumbnails.of(orientedImage)
                    .size(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
                    .outputFormat(extension)
                    .toFile(thumbnailFile);

            Thumbnails.of(orientedImage)
                    .size(MEDIUM_SIZE, MEDIUM_SIZE)
                    .outputFormat(extension)
                    .toFile(mediumFile);

            int width = orientedImage.getWidth();
            int height = orientedImage.getHeight();
            int targetWidth = width;
            int targetHeight = height;
            if (width > FULL_SIZE_CAP || height > FULL_SIZE_CAP) {
                double scale = Math.min((double) FULL_SIZE_CAP / width, (double) FULL_SIZE_CAP / height);
                targetWidth = Math.max(1, (int) Math.round(width * scale));
                targetHeight = Math.max(1, (int) Math.round(height * scale));
            }

            Thumbnails.of(orientedImage)
                    .size(targetWidth, targetHeight)
                    .outputFormat(extension)
                    .toFile(fullFile);

            return new StoredImagePaths(thumbnailRelativePath, mediumRelativePath, fullRelativePath);
        } catch (IOException e) {
            throw new InvalidImageException("The uploaded file could not be processed as an image");
        }
    }

    public void deleteFiles(ProductImage image) {
        deleteIfExists(image.getThumbnailPath());
        deleteIfExists(image.getMediumPath());
        deleteIfExists(image.getFullPath());
    }

    private void deleteIfExists(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(storageLocation, relativePath));
        } catch (IOException e) {
            // best-effort cleanup; a file already missing is not an error for this operation
        }
    }

    /**
     * Validates the declared content type and size up front, then decodes the actual bytes
     * to confirm the upload is a genuine, decodable image of an accepted type. Returns the
     * normalized file extension ("jpg", "png" or "webp") to use for the stored variants,
     * derived from the actually decoded image format rather than the client-supplied filename.
     */
    private String detectAndValidateFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("No file was provided");
        }
        String declaredContentType = file.getContentType();
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType.toLowerCase())) {
            throw new InvalidImageException(
                    "Unsupported file type: " + declaredContentType + ". Accepted formats are JPEG, PNG and WebP.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidImageException("File exceeds the maximum allowed size of 5MB");
        }

        try (ImageInputStream iis = ImageIO.createImageInputStream(file.getInputStream())) {
            if (iis == null) {
                throw new InvalidImageException("The uploaded file is not a genuine image");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidImageException("The uploaded file is not a genuine image");
            }
            ImageReader reader = readers.next();
            try {
                String normalized = normalizeFormat(reader.getFormatName());
                if (normalized == null) {
                    throw new InvalidImageException(
                            "Unsupported image format: " + reader.getFormatName());
                }
                reader.setInput(iis);
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new InvalidImageException("The uploaded file could not be processed as an image");
                }
                return normalized;
            } catch (IOException e) {
                throw new InvalidImageException("The uploaded file is corrupted and could not be processed");
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new InvalidImageException("The uploaded file could not be processed as an image");
        }
    }

    private String normalizeFormat(String formatName) {
        if (formatName == null) {
            return null;
        }
        String lower = formatName.toLowerCase();
        if (lower.contains("jpeg") || lower.contains("jpg")) {
            return "jpg";
        }
        if (lower.contains("png")) {
            return "png";
        }
        if (lower.contains("webp")) {
            return "webp";
        }
        return null;
    }

    private BufferedImage applyExifAutoRotation(byte[] originalBytes, BufferedImage image) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(originalBytes));

            Integer orientation = null;
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                orientation = ifd0.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            }
            if (orientation == null) {
                ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
                if (subIfd != null) {
                    orientation = subIfd.getInteger(ExifSubIFDDirectory.TAG_ORIENTATION);
                }
            }

            if (orientation == null || orientation == 1) {
                return image;
            }

            // EXIF orientation values:
            // 3 = 180°, 6 = 90° CW, 8 = 90° CCW
            return switch (orientation) {
                case 3 -> Thumbnails.of(image).scale(1.0).rotate(180).asBufferedImage();
                case 6 -> Thumbnails.of(image).scale(1.0).rotate(90).asBufferedImage();
                case 8 -> Thumbnails.of(image).scale(1.0).rotate(-90).asBufferedImage();
                default -> image;
            };
        } catch (Exception e) {
            // Best-effort: if EXIF can't be read, keep the decoded image as-is.
            return image;
        }
    }
}
