package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.product.Product;
import com.example.localhostfacom.product.ProductRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ImageServiceTest {

    /** Records what was uploaded and deleted, so rollback behaviour is observable. */
    static class RecordingStorageProvider implements StorageProvider {
        final List<String> uploaded = new ArrayList<>();
        final List<String> deleted = new ArrayList<>();
        boolean failUploads = false;

        @Override public void upload(String key, InputStream body, long size, String mimeType) {
            if (failUploads) {
                throw ApiException.badGateway("storage-upload-failed", "boom");
            }
            uploaded.add(key);
        }
        @Override public void delete(String key) { deleted.add(key); }
        @Override public String publicUrl(String key) { return "http://storage.test/" + key; }
        @Override public String presignDownloadUrl(String key, Duration expires) { return publicUrl(key); }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary RecordingStorageProvider recordingStorageProvider() {
            return new RecordingStorageProvider();
        }
    }

    @Autowired private ImageService service;
    @Autowired private ImageRepository images;
    @Autowired private ProductRepository products;
    @Autowired private RecordingStorageProvider storage;

    private byte[] png(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @BeforeEach
    void setUp() {
        products.deleteAll();
        images.deleteAll();
        storage.uploaded.clear();
        storage.deleted.clear();
        storage.failUploads = false;
    }

    @Test
    void uploadsAndStoresAnImage() throws Exception {
        Image image = service.uploadAndSave(png(64));

        assertThat(image.getStorageKey()).startsWith("products/").endsWith(".jpg");
        assertThat(storage.uploaded).hasSize(1);
        assertThat(images.findById(image.getId())).isPresent();
    }

    /** The same bytes must never occupy the bucket twice. */
    @Test
    void deduplicatesByHashWithoutUploadingAgain() throws Exception {
        byte[] source = png(64);
        Image first = service.uploadAndSave(source);
        Image second = service.uploadAndSave(source);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(storage.uploaded).hasSize(1);
        assertThat(images.count()).isEqualTo(1L);
    }

    @Test
    void reportsAStorageFailureWithoutWritingARow() throws Exception {
        storage.failUploads = true;

        assertThatThrownBy(() -> service.uploadAndSave(png(64)))
                .isInstanceOf(ApiException.class);
        assertThat(images.count()).isZero();
    }

    @Test
    void refusesToDeleteAnImageAProductStillReferences() throws Exception {
        Image image = service.uploadAndSave(png(64));
        products.save(Product.create("Café", new BigDecimal("3.50"), image));

        assertThatThrownBy(() -> service.delete(image.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("in use");
        assertThat(images.findById(image.getId())).isPresent();
    }

    @Test
    void deletesAnUnreferencedImageFromBothTheDatabaseAndStorage() throws Exception {
        Image image = service.uploadAndSave(png(64));

        service.delete(image.getId());

        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(storage.deleted).containsExactly(image.getStorageKey());
    }
}
