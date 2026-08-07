package com.example.localhostfacom.product;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Filled in fully by the products task; for now it only needs to exist and return 200,
// so the "public routes need no token" security test has something to hit.
@RestController
@RequestMapping("/api/public/products")
public class PublicProductController {

    @GetMapping
    public List<Object> list() {
        return List.of();
    }
}
