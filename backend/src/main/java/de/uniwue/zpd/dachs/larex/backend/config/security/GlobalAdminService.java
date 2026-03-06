package de.uniwue.zpd.dachs.larex.backend.config.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class GlobalAdminService {

    private static final String GLOBAL_ADMIN_ROLE = "ROLE_GLOBAL_ADMIN";
    private static final String GLOBAL_CURATOR_ROLE = "ROLE_GLOBAL_CURATOR";

    public boolean isGlobalAdmin() {
        return isGlobalAdmin(SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean isGlobalAdmin(Authentication auth) {
        return hasRole(auth, GLOBAL_ADMIN_ROLE);
    }

    public boolean isGlobalCurator() {
        return isGlobalCurator(SecurityContextHolder.getContext().getAuthentication());
    }

    public boolean isGlobalCurator(Authentication auth) {
        return hasRole(auth, GLOBAL_CURATOR_ROLE);
    }

    public boolean canCreateWorkspaces() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return isGlobalAdmin(auth) || isGlobalCurator(auth);
    }

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()));
    }
}
