package com.example.localhostfacom.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "admin")
public class Admin {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Admin() {}

    public static Admin create(String email, String passwordHash) {
        Admin admin = new Admin();
        admin.id = UUID.randomUUID();
        admin.email = email.trim().toLowerCase(Locale.ROOT);
        admin.passwordHash = passwordHash;
        admin.active = true;
        admin.createdAt = Instant.now();
        return admin;
    }

    public void deactivate() {
        this.active = false;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
