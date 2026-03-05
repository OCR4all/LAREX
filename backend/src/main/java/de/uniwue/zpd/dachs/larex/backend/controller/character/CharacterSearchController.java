package de.uniwue.zpd.dachs.larex.backend.controller.character;

import de.uniwue.zpd.dachs.larex.backend.dto.CharacterSearchDto;
import de.uniwue.zpd.dachs.larex.backend.service.character.CharacterEntry;
import de.uniwue.zpd.dachs.larex.backend.service.character.CharacterSearchService;
import de.uniwue.zpd.dachs.larex.backend.service.character.CharacterSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/characters")
public class CharacterSearchController {

    private final CharacterSearchService characterSearchService;

    public CharacterSearchController(CharacterSearchService characterSearchService) {
        this.characterSearchService = characterSearchService;
    }

    @GetMapping("/search")
    public ResponseEntity<CharacterSearchDto.Response> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int offset,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false) List<String> source,
            @RequestParam(required = false) Boolean isPua,

            // Unicode facets
            @RequestParam(required = false, name = "generalCategory") List<String> generalCategories,

            // MUFI facets
            @RequestParam(required = false, name = "mufiRange") List<String> mufiRanges,
            @RequestParam(required = false, name = "mufiStatus") List<String> mufiStatuses,
            @RequestParam(required = false, name = "mufiVersion") List<String> mufiVersions,
            @RequestParam(required = false) Boolean deprecated,

            @AuthenticationPrincipal(expression = "subject") String userId
    ) {
        Set<CharacterSource> sources = parseSources(source);

        CharacterSearchService.SearchResult result = characterSearchService.search(
                q,
                offset,
                limit,
                sources,
                isPua,
                toSet(generalCategories),
                toSet(mufiRanges),
                toSet(mufiStatuses),
                toSet(mufiVersions),
                deprecated
        );

        List<CharacterSearchDto.Item> items = result.items().stream().map(CharacterSearchController::toDto).toList();
        Map<String, List<CharacterSearchDto.FacetValue>> facets = toFacetDto(result.facets());

        return ResponseEntity.ok(new CharacterSearchDto.Response(
                result.query(),
                result.offset(),
                result.limit(),
                result.total(),
                items,
                facets
        ));
    }

    private static CharacterSearchDto.Item toDto(CharacterEntry e) {
        String hex = String.format("%04X", e.codePoint());
        String utf8 = new String(Character.toChars(e.codePoint()));

        return new CharacterSearchDto.Item(
                e.source().name().toLowerCase(Locale.ROOT),
                e.codePoint(),
                hex,
                utf8,
                e.description(),
                e.isPua(),
                e.generalCategory(),
                e.mufiRange(),
                e.mufiVersion(),
                e.mufiStatus(),
                e.source() == CharacterSource.MUFI ? e.deprecated() : null,
                e.url()
        );
    }

    private static Map<String, List<CharacterSearchDto.FacetValue>> toFacetDto(Map<String, Map<String, Long>> facets) {
        Map<String, List<CharacterSearchDto.FacetValue>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> entry : facets.entrySet()) {
            List<CharacterSearchDto.FacetValue> vals = entry.getValue().entrySet().stream()
                    .map(e -> new CharacterSearchDto.FacetValue(e.getKey(), e.getValue()))
                    .toList();
            out.put(entry.getKey(), vals);
        }
        return out;
    }

    private static Set<String> toSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> out = new HashSet<>();
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.isEmpty() ? null : out;
    }

    private static Set<CharacterSource> parseSources(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return EnumSet.allOf(CharacterSource.class);
        }
        EnumSet<CharacterSource> out = EnumSet.noneOf(CharacterSource.class);
        for (String s : raw) {
            if (s == null) continue;
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty() || t.equals("all")) {
                return EnumSet.allOf(CharacterSource.class);
            }
            if (t.equals("unicode")) out.add(CharacterSource.UNICODE);
            if (t.equals("mufi")) out.add(CharacterSource.MUFI);
        }
        return out.isEmpty() ? EnumSet.allOf(CharacterSource.class) : out;
    }
}
