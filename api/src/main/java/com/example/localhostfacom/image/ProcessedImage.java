package com.example.localhostfacom.image;

public record ProcessedImage(byte[] bytes, int width, int height, String hash, String mimeType) {

    public String extension() {
        return "image/png".equals(mimeType) ? "png" : "jpg";
    }

    public long size() {
        return bytes.length;
    }
}
