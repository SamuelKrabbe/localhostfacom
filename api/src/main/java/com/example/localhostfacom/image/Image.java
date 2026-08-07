package com.example.localhostfacom.image;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "image")
public class Image {

    @Id
    private UUID id;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    private int width;
    private int height;

    @Column(nullable = false, unique = true, length = 64)
    private String hash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Image() {}

    public static Image create(String storageKey, String mimeType, int width, int height, String hash) {
        Image image = new Image();
        image.id = UUID.randomUUID();
        image.storageKey = storageKey;
        image.mimeType = mimeType;
        image.width = width;
        image.height = height;
        image.hash = hash;
        image.createdAt = Instant.now();
        return image;
    }

    public UUID getId() { return id; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getHash() { return hash; }
    public Instant getCreatedAt() { return createdAt; }
}
