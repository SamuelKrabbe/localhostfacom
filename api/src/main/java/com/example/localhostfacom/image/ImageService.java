package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.product.ProductRepository;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImageService {

    private static final Logger log = LoggerFactory.getLogger(ImageService.class);
    private static final int MAX_DIMENSION = 1024;

    private final ImageRepository images;
    private final ProductRepository products;
    private final ImageProcessor processor;
    private final StorageProvider storage;

    public ImageService(ImageRepository images, ProductRepository products,
                        ImageProcessor processor, StorageProvider storage) {
        this.images = images;
        this.products = products;
        this.processor = processor;
        this.storage = storage;
    }

    /**
     * Deliberately NOT @Transactional. It performs a network upload, and it recovers from
     * a unique-constraint violation by re-reading the winning row — inside a transaction
     * that violation would mark the context rollback-only and the recovery read would fail
     * too. Each repository call manages its own transaction instead.
     */
    public Image uploadAndSave(byte[] source) {
        ProcessedImage processed = processor.process(source, MAX_DIMENSION);

        var existing = images.findByHash(processed.hash());
        if (existing.isPresent()) {
            return existing.get();
        }

        String key = "products/" + UUID.randomUUID() + "." + processed.extension();
        storage.upload(key, new ByteArrayInputStream(processed.bytes()), processed.size(), processed.mimeType());

        try {
            return images.saveAndFlush(Image.create(
                    key, processed.mimeType(), processed.width(), processed.height(), processed.hash()));
        } catch (DataIntegrityViolationException exception) {
            // Never leave an object in the bucket that no row points at.
            storage.delete(key);

            // Another request uploaded the same bytes concurrently and won the race on the
            // hash constraint. Its row is just as good as the one we failed to write.
            return images.findByHash(processed.hash()).orElseThrow(() ->
                    ApiException.conflict("image-save-failed", "Could not save the image"));
        }
    }

    @Transactional
    public void delete(UUID id) {
        Image image = images.findById(id)
                .orElseThrow(() -> ApiException.notFound("image-not-found", "Image not found"));

        if (products.existsByImageId(id)) {
            throw ApiException.conflict("image-in-use", "This image is in use by a product");
        }

        images.delete(image);

        try {
            storage.delete(image.getStorageKey());
        } catch (RuntimeException exception) {
            // The row is gone, which is what the caller asked for. An orphaned object costs
            // a few kilobytes; failing here would leave the caller thinking nothing happened.
            log.warn("Deleted image row {} but could not remove storage key {}",
                    id, image.getStorageKey(), exception);
        }
    }

    public String publicUrl(Image image) {
        return image == null ? null : storage.publicUrl(image.getStorageKey());
    }
}
