package com.example.localhostfacom.admin;

import com.example.localhostfacom.common.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;

    public AdminService(AdminRepository admins, PasswordEncoder passwordEncoder) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Admin> list() {
        return admins.findAllByOrderByCreatedAtAsc();
    }

    @Transactional
    public Admin create(String email, String password) {
        if (admins.existsByEmailIgnoreCase(email.trim())) {
            throw ApiException.conflict("admin-exists", "An admin with that email already exists");
        }
        return admins.save(Admin.create(email, passwordEncoder.encode(password)));
    }

    @Transactional
    public void remove(UUID targetId, UUID callerId) {
        if (targetId.equals(callerId)) {
            throw ApiException.conflict("cannot-remove-self", "You cannot remove yourself");
        }

        Admin target = admins.findById(targetId)
                .orElseThrow(() -> ApiException.notFound("admin-not-found", "Admin not found"));

        if (!target.isActive()) {
            return;
        }

        if (admins.countByActiveTrue() <= 1) {
            throw ApiException.conflict("cannot-remove-last-admin",
                    "Cannot remove the last active admin");
        }

        // Deactivated rather than deleted: expenses reference the admin who recorded them,
        // and orders reference whoever confirmed a payment by hand.
        target.deactivate();
        admins.save(target);
    }
}
