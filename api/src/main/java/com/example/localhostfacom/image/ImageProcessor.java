package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

@Component
public class ImageProcessor {

    /**
     * Caps the SOURCE dimensions, checked from the file header before the full decode.
     * Without it, a small but heavily compressed file declaring an enormous pixel count
     * would force a huge allocation during decode — long before any resize could help.
     */
    private static final int MAX_SOURCE_DIM = 8192;

    private static final float JPEG_QUALITY = 0.85f;

    public ProcessedImage process(byte[] source, int maxDim) {
        String hash = sha256(source);

        int[] dimensions = readDimensionsFromHeader(source);
        int sourceWidth = dimensions[0];
        int sourceHeight = dimensions[1];

        if (sourceWidth > MAX_SOURCE_DIM || sourceHeight > MAX_SOURCE_DIM) {
            throw ApiException.badRequest("image-too-large",
                    "Image dimensions are too large; the maximum is " + MAX_SOURCE_DIM + " pixels per side");
        }

        BufferedImage decoded = decode(source);
        boolean hasAlpha = decoded.getColorModel().hasAlpha();

        BufferedImage resized = decoded;
        if (sourceWidth > maxDim || sourceHeight > maxDim) {
            try {
                resized = Thumbnails.of(decoded).size(maxDim, maxDim).keepAspectRatio(true).asBufferedImage();
            } catch (IOException exception) {
                throw ApiException.badRequest("image-resize-failed", "Could not resize the image");
            }
        }

        String mimeType = hasAlpha ? "image/png" : "image/jpeg";
        byte[] encoded = encode(resized, hasAlpha);

        return new ProcessedImage(encoded, resized.getWidth(), resized.getHeight(), hash, mimeType);
    }

    private int[] readDimensionsFromHeader(byte[] source) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw ApiException.badRequest("unsupported-image", "Unsupported image format");
        }
    }

    private BufferedImage decode(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                throw ApiException.badRequest("unsupported-image", "Unsupported image format");
            }
            return image;
        } catch (IOException exception) {
            throw ApiException.badRequest("unsupported-image", "Unsupported image format");
        }
    }

    private byte[] encode(BufferedImage image, boolean hasAlpha) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (hasAlpha) {
                Thumbnails.of(image).scale(1.0).outputFormat("png").toOutputStream(out);
            } else {
                Thumbnails.of(image).scale(1.0).outputFormat("jpg")
                        .outputQuality(JPEG_QUALITY).toOutputStream(out);
            }
        } catch (IOException exception) {
            throw ApiException.badRequest("image-encode-failed", "Could not encode the image");
        }
        return out.toByteArray();
    }

    private String sha256(byte[] source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required but unavailable", exception);
        }
    }
}
