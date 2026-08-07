package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import com.example.localhostfacom.auth.dto.LoginRequest;
import com.example.localhostfacom.auth.dto.LoginResponse;
import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.common.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;

    public AuthController(AdminRepository admins, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RateLimiter rateLimiter) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        if (!rateLimiter.tryAcquire("login:" + http.getRemoteAddr(), 5, Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("rate-limited", "Too many login attempts");
        }

        Optional<Admin> admin = admins.findByEmailIgnoreCase(request.email()).filter(Admin::isActive);

        // Verify against a dummy hash when the account is missing, so a failed lookup and a
        // wrong password take the same amount of time and cannot be told apart.
        String storedHash = admin.map(Admin::getPasswordHash)
                .orElse("$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv");

        if (!passwordEncoder.matches(request.password(), storedHash) || admin.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid-credentials", "Invalid email or password");
        }

        return new LoginResponse(jwtService.issue(admin.get()), admin.get().getEmail(), jwtService.expiresAt());
    }
}
