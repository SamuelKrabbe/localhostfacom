package com.example.localhostfacom.product;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();

    // JPQL has no boolean-returning comparison in the SELECT list, so the CASE is required.
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END "
            + "FROM OrderItem i WHERE i.productId = :productId")
    boolean hasBeenOrdered(UUID productId);

    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END "
            + "FROM Product p WHERE p.image.id = :imageId")
    boolean existsByImageId(UUID imageId);
}
