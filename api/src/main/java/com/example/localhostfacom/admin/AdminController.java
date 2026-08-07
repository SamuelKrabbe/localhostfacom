package com.example.localhostfacom.admin;

import com.example.localhostfacom.auth.CurrentAdmin;
import com.example.localhostfacom.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record AdminResponse(UUID id, String email, boolean active, Instant createdAt) {
        static AdminResponse of(Admin admin) {
            return new AdminResponse(admin.getId(), admin.getEmail(), admin.isActive(), admin.getCreatedAt());
        }
    }

    public record CreateAdminRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    private final AdminRepository admins;
    private final AdminService service;

    public AdminController(AdminRepository admins, AdminService service) {
        this.admins = admins;
        this.service = service;
    }

    @GetMapping("/me")
    public AdminResponse me() {
        return admins.findById(CurrentAdmin.require())
                .map(AdminResponse::of)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));
    }

    @GetMapping("/admins")
    public List<AdminResponse> list() {
        return service.list().stream().map(AdminResponse::of).toList();
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@Valid @RequestBody CreateAdminRequest request) {
        return AdminResponse.of(service.create(request.email(), request.password()));
    }

    @DeleteMapping("/admins/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        service.remove(id, CurrentAdmin.require());
    }
}
