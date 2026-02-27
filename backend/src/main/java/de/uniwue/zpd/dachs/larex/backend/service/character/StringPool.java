package de.uniwue.zpd.dachs.larex.backend.service.character;

import java.util.HashMap;
import java.util.Map;

final class StringPool {

    private final Map<String, String> pool = new HashMap<>(16_384);

    String pool(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String existing = pool.get(trimmed);
        if (existing != null) {
            return existing;
        }
        pool.put(trimmed, trimmed);
        return trimmed;
    }
}
