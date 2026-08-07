package com.example.localhostfacom.product;

import com.example.localhostfacom.image.ImageService;
import com.example.localhostfacom.product.dto.ProductRequest;
import com.example.localhostfacom.product.dto.ProductResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final ProductService products;
    private final ImageService images;

    public AdminProductController(ProductService products, ImageService images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return products.listAll().stream().map(this::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return toResponse(products.create(request.name(), request.price(), request.imageId()));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return toResponse(products.update(
                id, request.name(), request.price(), request.imageId(), request.activeOrDefault()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        products.remove(id);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.of(product, images.publicUrl(product.getImage()));
    }
}
