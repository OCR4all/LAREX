package de.uniwue.zpd.dachs.larex.backend.service.action;

import java.util.Locale;

public final class ActionEndpointSecretRef {

    public static final String PATTERN = "[a-zA-Z0-9][a-zA-Z0-9._-]{1,126}";

    private ActionEndpointSecretRef() {
    }

    public static boolean isValid(String secretRef) {
        return secretRef != null && !secretRef.isBlank() && secretRef.trim().matches(PATTERN);
    }

    public static String normalizeForStorage(String secretRef) {
        String envRef = normalizeForEnv(secretRef);
        if (envRef == null) {
            return null;
        }
        return envRef.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static String normalizeForEnv(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        String normalized = secretRef.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return normalized.isBlank() ? null : normalized;
    }
}
