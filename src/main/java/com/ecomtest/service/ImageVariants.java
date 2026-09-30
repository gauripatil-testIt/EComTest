package com.ecomtest.service;

/**
 * Holds the three resized derivatives produced from a single uploaded image, along with the
 * content type/extension chosen for all three (JPEG when the source has no transparency,
 * PNG when it does).
 */
public class ImageVariants {

    private final byte[] thumbnail;
    private final byte[] medium;
    private final byte[] full;
    private final String contentType;
    private final String extension;

    public ImageVariants(byte[] thumbnail, byte[] medium, byte[] full, String contentType, String extension) {
        this.thumbnail = thumbnail;
        this.medium = medium;
        this.full = full;
        this.contentType = contentType;
        this.extension = extension;
    }

    public byte[] getThumbnail() {
        return thumbnail;
    }

    public byte[] getMedium() {
        return medium;
    }

    public byte[] getFull() {
        return full;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }
}
