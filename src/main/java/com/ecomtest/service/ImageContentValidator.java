package com.ecomtest.service;

import com.ecomtest.exception.ImageTooLargeException;
import com.ecomtest.exception.InvalidImageException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * Validates uploaded product images by sniffing the actual byte content
 * (rather than trusting the filename/extension) and enforcing the size cap.
 */
@Component
public class ImageContentValidator {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Tika tika = new Tika();

    /**
     * Validates the given file and returns its sniffed content type.
     *
     * @throws ImageTooLargeException if the file exceeds the maximum allowed size
     * @throws InvalidImageException  if the file is empty, unreadable, or not one of the
     *                                 supported image types based on its actual content
     */
    public String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("Uploaded file is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ImageTooLargeException("File exceeds maximum allowed size of 5MB");
        }

        String detectedContentType;
        try {
            detectedContentType = tika.detect(file.getInputStream());
        } catch (IOException e) {
            throw new InvalidImageException("Unable to read uploaded file content");
        }

        if (!ALLOWED_CONTENT_TYPES.contains(detectedContentType)) {
            throw new InvalidImageException("Unsupported image content type: " + detectedContentType);
        }

        return detectedContentType;
    }
}
