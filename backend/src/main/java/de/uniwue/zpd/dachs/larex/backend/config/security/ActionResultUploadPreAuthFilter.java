package de.uniwue.zpd.dachs.larex.backend.config.security;

import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionResultUploadPreAuthFilter extends OncePerRequestFilter {

    private static final Pattern RESULT_UPLOAD_PATH = Pattern.compile("^/public/actions/runs/([^/]+)/results/?$");
    private static final String API_PREFIX = "/api/v1";

    private final ActionRunService actionRunService;

    public ActionResultUploadPreAuthFilter(ActionRunService actionRunService) {
        this.actionRunService = actionRunService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String normalizedPath = normalizedPath(request);
        Matcher matcher = RESULT_UPLOAD_PATH.matcher(normalizedPath);
        if (!"POST".equalsIgnoreCase(request.getMethod()) || !matcher.matches()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (!hasBearerToken(authorizationHeader)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Action run secret");
            return;
        }

        String runId = java.net.URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        try {
            actionRunService.authenticateMachineRun(runId, authorizationHeader);
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Action run secret");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        if (path.startsWith(API_PREFIX + "/")) {
            path = path.substring(API_PREFIX.length());
        }
        return path;
    }

    private boolean hasBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return false;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return false;
        }
        return !authorizationHeader.substring(prefix.length()).trim().isEmpty();
    }
}
