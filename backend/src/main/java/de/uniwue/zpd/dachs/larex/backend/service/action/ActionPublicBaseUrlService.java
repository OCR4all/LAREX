package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ActionPublicBaseUrlService {

    private final String configuredPublicBaseUrl;
    private final List<String> allowedOrigins;
    private final boolean requireHttps;

    public ActionPublicBaseUrlService(ActionProperties actionProperties) {
        this.configuredPublicBaseUrl = trimToNull(actionProperties.getPublicBaseUrl());
        this.allowedOrigins = splitList(actionProperties.getPublicBaseUrlAllowedOrigins());
        this.requireHttps = actionProperties.isPublicBaseUrlRequireHttps();
    }

    public String publicApiBaseUrl(HttpServletRequest request) {
        if (configuredPublicBaseUrl != null) {
            return validateAndNormalize(configuredPublicBaseUrl, false);
        }
        String root = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .build()
                .toUriString();
        return validateAndNormalize(root + "/api/v1", true);
    }

    private String validateAndNormalize(String rawUrl, boolean derivedFromRequest) {
        String normalized = rawUrl.replaceAll("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid LAREX Actions public base URL", e);
        }

        String scheme = lower(uri.getScheme());
        String host = lower(uri.getHost());
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalStateException("LAREX Actions public base URL must use http or https");
        }
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("LAREX Actions public base URL must include a host");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalStateException("LAREX Actions public base URL must not include user info");
        }
        if (requireHttps && !"https".equals(scheme) && !isLocalOrInternalHost(host)) {
            throw new SecurityException("LAREX Actions public base URL must use https for non-local hosts");
        }
        if (derivedFromRequest && !isAllowed(uri)) {
            throw new SecurityException("LAREX Actions public base URL origin is not allowlisted: " + origin(uri));
        }
        return normalized;
    }

    private boolean isAllowed(URI uri) {
        if (allowedOrigins.isEmpty()) {
            return false;
        }
        return allowedOrigins.stream().anyMatch(allowed -> matchesAllowedOrigin(allowed, uri));
    }

    private boolean matchesAllowedOrigin(String allowed, URI actual) {
        AllowedOrigin parsed = parseAllowedOrigin(allowed);
        if (parsed == null || parsed.host() == null) {
            return false;
        }
        if (parsed.scheme() != null && !Objects.equals(parsed.scheme(), lower(actual.getScheme()))) {
            return false;
        }
        if (!hostMatches(parsed.host(), lower(actual.getHost()))) {
            return false;
        }
        return parsed.port() == null || parsed.port() == effectivePort(actual);
    }

    private AllowedOrigin parseAllowedOrigin(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.contains("://")) {
            try {
                URI uri = URI.create(trimmed);
                return new AllowedOrigin(lower(uri.getScheme()), lower(uri.getHost()), effectivePort(uri));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        String host = trimmed;
        Integer port = null;
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0 && trimmed.indexOf(':') == colon) {
            String portPart = trimmed.substring(colon + 1);
            try {
                port = Integer.parseInt(portPart);
                host = trimmed.substring(0, colon);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return new AllowedOrigin(null, lower(host), port);
    }

    private boolean hostMatches(String allowedHost, String actualHost) {
        if (allowedHost == null || actualHost == null) {
            return false;
        }
        if (allowedHost.startsWith("*.")) {
            String suffix = allowedHost.substring(1);
            return actualHost.endsWith(suffix) && actualHost.length() > suffix.length();
        }
        return allowedHost.equals(actualHost);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        String scheme = lower(uri.getScheme());
        if ("https".equals(scheme)) {
            return 443;
        }
        if ("http".equals(scheme)) {
            return 80;
        }
        return -1;
    }

    private String origin(URI uri) {
        return lower(uri.getScheme()) + "://" + lower(uri.getHost()) + ":" + effectivePort(uri);
    }

    private boolean isLocalOrInternalHost(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || !host.contains(".")
                || host.startsWith("127.")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*");
    }

    private List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private record AllowedOrigin(String scheme, String host, Integer port) {}
}
