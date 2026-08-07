package com.example.localhostfacom.auth;

import com.example.localhostfacom.admin.Admin;
import com.example.localhostfacom.admin.AdminRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminRepository admins;

    public JwtAuthenticationFilter(JwtService jwtService, AdminRepository admins) {
        this.jwtService = jwtService;
        this.admins = admins;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            UUID adminId = jwtService.extractAdminId(header.substring(7));
            if (adminId != null) {
                // Re-read the row on every request. A stateless token cannot be revoked,
                // and the admin role rotates, so a removed admin must lose access at once
                // rather than when their token happens to expire.
                Optional<Admin> admin = admins.findById(adminId).filter(Admin::isActive);
                admin.ifPresent(value -> {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            value.getId(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
