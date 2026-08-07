package com.example.localhostfacom.admin;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.common.ApiException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record AdminResponse(UUID id, String email, boolean active, Instant createdAt) {
        static AdminResponse of(Admin admin) {
            return new AdminResponse(admin.getId(), admin.getEmail(), admin.isActive(), admin.getCreatedAt());
        }
    }

    private final AdminRepository admins;

    public AdminController(AdminRepository admins) {
        this.admins = admins;
    }

    @GetMapping("/me")
    public AdminResponse me() {
        UUID id = CurrentAdmin.require();
        return admins.findById(id)
                .map(AdminResponse::of)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));
    }
}
