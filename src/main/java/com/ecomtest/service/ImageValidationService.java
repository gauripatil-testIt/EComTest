package com.ecomtest.service;

import com.ecomtest.exception.ImageTooLargeException;
import com.ecomtest.exception.InvalidImageFileException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Service
public class ImageValidationService {

    public static final long MAX_SIZE_BYTES = ImageTooLargeException.MAX_SIZE_BYTES;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageFileException("Uploaded file is empty");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ImageTooLargeException("Image exceeds the maximum allowed size of 5MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidImageFileException(
                    "Unsupported file type '" + contentType + "'. Only JPEG, PNG and WebP images are accepted");
        }

        BufferedImage image;
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
        } catch (IOException e) {
            throw new InvalidImageFileException("The uploaded file is corrupted and could not be read");
        }

        if (image == null) {
            throw new InvalidImageFileException("The uploaded file is not a genuine image");
        }
    }
}
