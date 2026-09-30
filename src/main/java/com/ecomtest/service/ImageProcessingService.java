package com.ecomtest.service;

import com.ecomtest.exception.InvalidImageException;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Sniffs uploaded bytes to confirm they are a genuine image of an accepted format, then produces
 * thumbnail/medium/full derivatives with capped dimensions, preserving aspect ratio.
 */
@Service
public class ImageProcessingService {

    public static final int THUMBNAIL_MAX = 150;
    public static final int MEDIUM_MAX = 600;
    public static final int FULL_MAX = 2000;
    private static final float JPEG_QUALITY = 0.8f;

    private static final Set<String> ACCEPTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Tika tika = new Tika();

    /**
     * Detects the genuine content type of the given bytes by sniffing magic bytes rather than
     * trusting the filename/extension. Throws if the content is not one of the accepted image
     * formats.
     */
    public String detectGenuineContentType(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException("Uploaded file is empty");
        }
        String detected = tika.detect(bytes);
        if (!ACCEPTED_CONTENT_TYPES.contains(detected)) {
            throw new InvalidImageException("Unsupported image type: " + detected
                    + ". Accepted formats are JPEG, PNG and WebP.");
        }
        return detected;
    }

    /**
     * Decodes the given bytes as an image and produces thumbnail/medium/full derivatives.
     * Throws InvalidImageException if the bytes cannot be decoded as an image (i.e. the file is
     * corrupted despite passing content-type sniffing).
     */
    public ImageVariants generateVariants(byte[] bytes) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new InvalidImageException("Could not read image content: " + e.getMessage());
        }
        if (source == null) {
            throw new InvalidImageException("Uploaded file is not a readable image");
        }

        boolean hasAlpha = source.getColorModel().hasAlpha();
        String contentType = hasAlpha ? "image/png" : "image/jpeg";
        String extension = hasAlpha ? "png" : "jpg";

        byte[] thumbnail = resizeAndEncode(source, THUMBNAIL_MAX, contentType);
        byte[] medium = resizeAndEncode(source, MEDIUM_MAX, contentType);
        byte[] full = resizeAndEncode(source, FULL_MAX, contentType);

        return new ImageVariants(thumbnail, medium, full, contentType, extension);
    }

    private byte[] resizeAndEncode(BufferedImage source, int maxDimension, String contentType) {
        BufferedImage resized = resize(source, maxDimension);
        return encode(resized, contentType);
    }

    private BufferedImage resize(BufferedImage source, int maxDimension) {
        int width = source.getWidth();
        int height = source.getHeight();

        double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
        int targetWidth = Math.max(1, Math.round((float) (width * scale)));
        int targetHeight = Math.max(1, Math.round((float) (height * scale)));

        if (targetWidth == width && targetHeight == height) {
            return toCompatibleType(source);
        }

        BufferedImage target = new BufferedImage(targetWidth, targetHeight,
                source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private BufferedImage toCompatibleType(BufferedImage source) {
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        if (source.getType() == type) {
            return source;
        }
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return copy;
    }

    private byte[] encode(BufferedImage image, String contentType) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if ("image/jpeg".equals(contentType)) {
                writeJpeg(image, out);
            } else {
                ImageIO.write(image, "png", out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidImageException("Failed to encode resized image: " + e.getMessage());
        }
    }

    private void writeJpeg(BufferedImage image, ByteArrayOutputStream out) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new InvalidImageException("No JPEG writer available");
        }
        ImageWriter writer = writers.next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            }
        } finally {
            writer.dispose();
        }
    }
}
