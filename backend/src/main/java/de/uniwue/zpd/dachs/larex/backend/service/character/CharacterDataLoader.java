package de.uniwue.zpd.dachs.larex.backend.service.character;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static de.uniwue.zpd.dachs.larex.backend.service.character.CharacterPua.isPua;

final class CharacterDataLoader {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private CharacterDataLoader() {
    }

    static List<CharacterEntry> loadUnicodeData(InputStream in, StringPool pool) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<CharacterEntry> result = new ArrayList<>(150_000);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // UnicodeData.txt is semicolon separated with fixed fields.
                // 0: code point (hex)
                // 1: name
                // 2: general category
                String[] parts = splitUnicodeLine(line);
                if (parts.length < 3) {
                    continue;
                }

                int codePoint;
                try {
                    codePoint = Integer.parseInt(parts[0], 16);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                String name = pool.pool(parts[1]);
                String generalCategory = pool.pool(parts[2]);

                // Skip range placeholders like "<Hangul Syllable, First>" to keep results usable.
                // (If you want ranges too, we can add them later as separate pseudo-entries.)
                if (name != null && name.startsWith("<") && name.endsWith(">")) {
                    continue;
                }

                result.add(new CharacterEntry(
                        CharacterSource.UNICODE,
                        codePoint,
                        name,
                        isPua(codePoint),
                        generalCategory,
                        null,
                        null,
                        null,
                        false,
                        null
                ));
            }
            return result;
        }
    }

    static List<CharacterEntry> loadMufiData(InputStream in, StringPool pool) throws IOException {
        try (JsonParser parser = JSON_FACTORY.createParser(in)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException("Expected MUFI JSON array");
            }

            List<CharacterEntry> result = new ArrayList<>(10_000);

            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }

                String codepointHex = null;
                String description = null;
                String descriptionAlt = null;
                String range = null;
                String version = null;
                String status = null;
                String deprecated = null;
                String url = null;

                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String field = parser.currentName();
                    parser.nextToken();

                    if (field == null) {
                        parser.skipChildren();
                        continue;
                    }

                    switch (field) {
                        case "codepoint" -> codepointHex = parser.getValueAsString();
                        case "description" -> description = parser.getValueAsString();
                        case "descriptionalt" -> descriptionAlt = parser.getValueAsString();
                        case "range" -> range = parser.getValueAsString();
                        case "mufiversion" -> version = parser.getValueAsString();
                        case "mufi_status" -> status = parser.getValueAsString();
                        case "deprecated" -> deprecated = parser.getValueAsString();
                        case "url" -> url = parser.getValueAsString();
                        default -> parser.skipChildren();
                    }
                }

                if (codepointHex == null || codepointHex.isBlank()) {
                    continue;
                }

                int codePoint;
                try {
                    codePoint = Integer.parseInt(codepointHex, 16);
                } catch (NumberFormatException ignored) {
                    continue;
                }

                String mergedDescription = mergeDescriptions(description, descriptionAlt);

                result.add(new CharacterEntry(
                        CharacterSource.MUFI,
                        codePoint,
                        pool.pool(mergedDescription),
                        isPua(codePoint),
                        null,
                        pool.pool(range),
                        pool.pool(version),
                        pool.pool(status),
                        "1".equals(deprecated),
                        pool.pool(url)
                ));
            }

            return result;
        }
    }

    private static String mergeDescriptions(String description, String descriptionAlt) {
        String d = description == null ? "" : description.trim();
        String a = descriptionAlt == null ? "" : descriptionAlt.trim();
        if (!d.isEmpty() && !a.isEmpty() && !d.equalsIgnoreCase(a)) {
            return d + " / " + a;
        }
        return !d.isEmpty() ? d : (!a.isEmpty() ? a : null);
    }

    private static String[] splitUnicodeLine(String line) {
        // Avoid regex split; keep allocation low.
        // UnicodeData has exactly 15 fields, but we only need first 3.
        String[] out = new String[3];
        int start = 0;
        int field = 0;
        for (int i = 0; i < line.length() && field < 3; i++) {
            if (line.charAt(i) == ';') {
                out[field++] = line.substring(start, i);
                start = i + 1;
            }
        }
        if (field < 2) {
            return new String[0];
        }
        if (field == 2) {
            // last field needed (general category) ends at next ';' or EOL.
            int end = line.indexOf(';', start);
            out[2] = end >= 0 ? line.substring(start, end) : line.substring(start);
        }
        return out;
    }
}
