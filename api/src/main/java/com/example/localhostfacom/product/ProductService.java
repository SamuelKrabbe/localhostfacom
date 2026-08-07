package com.example.localhostfacom.product;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.image.Image;
import com.example.localhostfacom.image.ImageRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ImageRepository images;

    public ProductService(ProductRepository products, ImageRepository images) {
        this.products = products;
        this.images = images;
    }

    public List<Product> listActive() {
        return products.findByActiveTrueOrderByNameAsc();
    }

    public List<Product> listAll() {
        return products.findAllByOrderByNameAsc();
    }

    @Transactional
    public Product create(String name, BigDecimal price, UUID imageId) {
        return products.save(Product.create(name.trim(), price, resolveImage(imageId)));
    }

    @Transactional
    public Product update(UUID id, String name, BigDecimal price, UUID imageId, boolean active) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("product-not-found", "Product not found"));
        product.update(name.trim(), price, resolveImage(imageId), active);
        return products.save(product);
    }

    /**
     * Removes the row outright when the product has never been ordered, and deactivates it
     * otherwise. Either way the admin's intent — stop selling this — is satisfied, and a
     * product that has ever sold is never destroyed, so past orders keep their referent.
     */
    @Transactional
    public void remove(UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("product-not-found", "Product not found"));

        if (products.hasBeenOrdered(id)) {
            product.deactivate();
            products.save(product);
        } else {
            products.delete(product);
        }
    }

    public Product requireActive(UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.badRequest("product-not-found",
                        "Product " + id + " does not exist"));
        if (!product.isActive()) {
            throw ApiException.badRequest("product-inactive",
                    "Product " + product.getName() + " is not available");
        }
        return product;
    }

    private Image resolveImage(UUID imageId) {
        if (imageId == null) {
            return null;
        }
        return images.findById(imageId)
                .orElseThrow(() -> ApiException.badRequest("image-not-found", "Image not found"));
    }
}
