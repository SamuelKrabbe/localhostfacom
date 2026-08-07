package com.example.localhostfacom.image;

import com.example.localhostfacom.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StorageProviderTest {

    private S3CompatibleStorageProvider providerWithBase(String publicBaseUrl) {
        AppProperties.Storage storage = new AppProperties.Storage(
                "http://localhost:9000", "auto", "localhostfacom",
                "key", "secret", publicBaseUrl, true);
        return new S3CompatibleStorageProvider(storage);
    }

    @Test
    void buildsAPublicUrlFromTheConfiguredBase() {
        assertThat(providerWithBase("https://cdn.example.com/bucket").publicUrl("products/abc.jpg"))
                .isEqualTo("https://cdn.example.com/bucket/products/abc.jpg");
    }

    @Test
    void doesNotProduceADoubleSlashWhenTheBaseHasATrailingSlash() {
        assertThat(providerWithBase("https://cdn.example.com/bucket/").publicUrl("products/abc.jpg"))
                .isEqualTo("https://cdn.example.com/bucket/products/abc.jpg");
    }
}
