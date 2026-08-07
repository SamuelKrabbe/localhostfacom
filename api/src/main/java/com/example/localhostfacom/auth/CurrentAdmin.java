package com.example.localhostfacom.auth;

import com.example.localhostfacom.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAdmin {

    private CurrentAdmin() {}

    public static UUID require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UUID adminId)) {
            throw ApiException.forbidden("not-authenticated", "No authenticated admin in context");
        }
        return adminId;
    }
}
