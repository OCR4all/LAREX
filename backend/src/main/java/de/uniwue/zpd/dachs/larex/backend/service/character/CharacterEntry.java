package de.uniwue.zpd.dachs.larex.backend.service.character;

public record CharacterEntry(
        CharacterSource source,
        int codePoint,
        String description,
        boolean isPua,

        // Unicode
        String generalCategory,

        // MUFI
        String mufiRange,
        String mufiVersion,
        String mufiStatus,
        boolean deprecated,
        String url
) {
}
