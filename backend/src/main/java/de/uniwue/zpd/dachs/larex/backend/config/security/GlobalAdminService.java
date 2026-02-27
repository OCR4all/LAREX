package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class GlobalAdminService {

    private static final String GLOBAL_ADMIN_ROLE = "ROLE_GLOBAL_ADMIN";

    public boolean isGlobalAdmin() {
        return isGlobalAdmin(SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean isGlobalAdmin(Authentication auth) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> GLOBAL_ADMIN_ROLE.equals(a.getAuthority()));
    }
}
