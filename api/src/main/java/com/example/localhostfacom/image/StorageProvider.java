package com.example.localhostfacom.image;

import java.io.InputStream;
import java.time.Duration;

/** Abstracts the object store so a vendor swap is a configuration change. */
public interface StorageProvider {

    void upload(String key, InputStream body, long size, String mimeType);

    void delete(String key);

    String publicUrl(String key);

    /**
     * Unused while the bucket is public-read. It is declared so switching to a private
     * bucket later does not change this interface.
     */
    String presignDownloadUrl(String key, Duration expires);
}
