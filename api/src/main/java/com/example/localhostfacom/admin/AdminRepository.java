package com.example.localhostfacom.admin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, UUID> {
    Optional<Admin> findByEmailIgnoreCase(String email);
    long countByActiveTrue();
    List<Admin> findAllByOrderByCreatedAtAsc();
    boolean existsByEmailIgnoreCase(String email);
}
