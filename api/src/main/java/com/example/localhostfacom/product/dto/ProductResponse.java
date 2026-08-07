package com.example.localhostfacom.product.dto;

import com.example.localhostfacom.image.Image;
import com.example.localhostfacom.product.Product;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        BigDecimal price,
        String imageUrl,
        Integer imageWidth,
        Integer imageHeight,
        boolean active) {

    public static ProductResponse of(Product product, String imageUrl) {
        Image image = product.getImage();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                imageUrl,
                image == null ? null : image.getWidth(),
                image == null ? null : image.getHeight(),
                product.isActive());
    }
}
