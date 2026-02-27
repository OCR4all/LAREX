package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;
import java.util.Map;

public class CharacterSearchDto {

    public record Response(
            String query,
            int offset,
            int limit,
            int total,
            List<Item> items,
            Map<String, List<FacetValue>> facets
    ) {}

    public record Item(
            String source,
            int codePoint,
            String codePointHex,
            String utf8,
            String description,
            boolean isPua,

            // Unicode-only
            String generalCategory,

            // MUFI-only
            String mufiRange,
            String mufiVersion,
            String mufiStatus,
            Boolean deprecated,
            String url
    ) {}

    public record FacetValue(
            String value,
            long count
    ) {}
}
