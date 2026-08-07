package com.example.localhostfacom.product;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import com.example.localhostfacom.image.ImageService;
import com.example.localhostfacom.product.dto.ProductResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    private final ProductService products;
    private final ImageService images;
    private final RateLimiter rateLimiter;

    public PublicProductController(ProductService products, ImageService images, RateLimiter rateLimiter) {
        this.products = products;
        this.images = images;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping
    public List<ProductResponse> list(HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("products:" + http.getRemoteAddr(), 60, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many requests; please wait a moment");
        }

        return products.listActive().stream()
                .map(product -> ProductResponse.of(product, images.publicUrl(product.getImage())))
                .toList();
    }
}
