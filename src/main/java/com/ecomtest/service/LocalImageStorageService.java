package com.ecomtest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Stores product image files (originals and derived assets) on the local filesystem,
 * under a per-product/per-image subfolder, and resolves the relative keys it returns
 * into servable URLs based on the configured static base path.
 */
@Service
public class LocalImageStorageService {

    private final Path uploadRoot;
    private final String baseUrl;

    public LocalImageStorageService(
            @Value("${app.upload.dir:uploads}") String uploadDir,
            @Value("${app.upload.base-url:/uploads}") String baseUrl) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /**
     * Writes the given content under {@code <uploadDir>/products/<productId>/<imageId>/<filename>}
     * and returns the relative key (relative to the upload root) that identifies it.
     */
    public String store(Long productId, Long imageId, String filename, byte[] content) throws IOException {
        String relativeKey = "products/" + productId + "/" + imageId + "/" + filename;
        Path target = uploadRoot.resolve(relativeKey).normalize();
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return relativeKey;
    }

    /**
     * Deletes the file identified by the given relative key. Idempotent: does nothing
     * if the file does not exist.
     */
    public void delete(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            return;
        }
        Path target = uploadRoot.resolve(relativeKey).normalize();
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete stored image file: " + relativeKey, e);
        }
    }

    /**
     * Builds a servable URL for the given relative key, based on the configured
     * static base path (e.g. {@code /uploads}).
     */
    public String resolveUrl(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            return null;
        }
        String normalizedKey = relativeKey.startsWith("/") ? relativeKey.substring(1) : relativeKey;
        return baseUrl + "/" + normalizedKey;
    }

    private static String stripTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
