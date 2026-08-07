package com.example.localhostfacom.product;

import com.example.localhostfacom.image.ImageService;
import com.example.localhostfacom.product.dto.ProductResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private final ProductService products;
    private final ImageService images;

    public PublicProductController(ProductService products, ImageService images) {
        this.products = products;
        this.images = images;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return products.listActive().stream()
                .map(product -> ProductResponse.of(product, images.publicUrl(product.getImage())))
                .toList();
    }
}
