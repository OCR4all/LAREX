package de.uniwue.zpd.dachs.larex.backend.service.admin;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class ErrorEventContextResolver {

    private static final Pattern WORKSPACE_PATTERN = Pattern.compile("/workspaces/([^/]+)");

    public String resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }

        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : null;
    }

    public String resolveUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                return preferredUsername;
            }
        }

        String name = authentication.getName();
        return name != null && !name.isBlank() ? name : null;
    }

    public String resolveWorkspaceId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return null;
        }

        Matcher matcher = WORKSPACE_PATTERN.matcher(uri);
        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1);
    }
}
