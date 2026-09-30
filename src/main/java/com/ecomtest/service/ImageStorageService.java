package com.ecomtest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads/writes/deletes derived image files under the configurable upload directory. Keeps
 * persistence-of-bytes isolated from resizing/validation logic.
 */
@Service
public class ImageStorageService {

    private final String uploadDir;

    public ImageStorageService(@Value("${app.upload.dir}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /**
     * Writes the given bytes under {uploadDir}/products/{productId}/{imageId}_{sizeLabel}.{ext}
     * and returns the stored absolute path.
     */
    public String store(Long productId, Long imageId, String sizeLabel, String extension, byte[] bytes) {
        Path directory = Paths.get(uploadDir, "products", String.valueOf(productId));
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(imageId + "_" + sizeLabel + "." + extension);
            Files.write(target, bytes);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image file", e);
        }
    }

    /**
     * Removes a previously stored file. No-op if the path is null or the file does not exist.
     */
    public void delete(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete image file: " + path, e);
        }
    }

    /**
     * Loads the file at the given path as a Spring Resource, for streaming back via a controller.
     */
    public Resource loadAsResource(String path) {
        return new FileSystemResource(path);
    }
}
