package de.uniwue.zpd.dachs.larex.backend.service.character;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class CharacterSearchService {

    private static final String UNICODE_PATH = "data/unicode/UnicodeData.txt";
    private static final String MUFI_PATH = "data/unicode/mufi.json";

    private volatile Index index;

    void setIndexForTests(Index idx) {
        this.index = idx;
    }

    public SearchResult search(
            String query,
            int offset,
            int limit,
            Set<CharacterSource> sources,
            Boolean isPua,
            Set<String> generalCategories,
            Set<String> mufiRanges,
            Set<String> mufiStatuses,
            Set<String> mufiVersions,
            Boolean deprecated
    ) {
        Index idx = ensureIndex();

        String normalizedQuery = query == null ? "" : query.trim();
        List<String> tokens = Tokenizer.tokenize(normalizedQuery);

        Filter filter = new Filter(
                sources == null || sources.isEmpty() ? EnumSet.allOf(CharacterSource.class) : EnumSet.copyOf(sources),
                isPua,
                normalizeSet(generalCategories),
                normalizeSet(mufiRanges),
                normalizeSet(mufiStatuses),
                normalizeSet(mufiVersions),
                deprecated
        );

        if (limit <= 0) {
            limit = 20;
        }
        limit = Math.min(limit, 200);
        offset = Math.max(offset, 0);

        return idx.search(tokens, normalizedQuery, offset, limit, filter);
    }

    private static Set<String> normalizeSet(Set<String> in) {
        if (in == null || in.isEmpty()) {
            return null;
        }
        Set<String> out = new HashSet<>(in.size());
        for (String v : in) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out.isEmpty() ? null : out;
    }

    private Index ensureIndex() {
        Index local = index;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (index != null) {
                return index;
            }
            try {
                index = buildIndex();
                return index;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load Unicode/MUFI character data", e);
            }
        }
    }

    private Index buildIndex() throws IOException {
        StringPool pool = new StringPool();

        List<CharacterEntry> all = new ArrayList<>(160_000);

        try (InputStream in = new ClassPathResource(UNICODE_PATH).getInputStream()) {
            all.addAll(CharacterDataLoader.loadUnicodeData(in, pool));
        }
        try (InputStream in = new ClassPathResource(MUFI_PATH).getInputStream()) {
            all.addAll(CharacterDataLoader.loadMufiData(in, pool));
        }

        all.sort(Comparator.comparingInt(CharacterEntry::codePoint).thenComparing(e -> e.source().name()));
        return Index.build(all);
    }

    public record SearchResult(
            String query,
            int offset,
            int limit,
            int total,
            List<CharacterEntry> items,
            Map<String, Map<String, Long>> facets
    ) {
    }

    private record Filter(
            EnumSet<CharacterSource> sources,
            Boolean isPua,
            Set<String> generalCategories,
            Set<String> mufiRanges,
            Set<String> mufiStatuses,
            Set<String> mufiVersions,
            Boolean deprecated
    ) {

        boolean matches(CharacterEntry e) {
            if (!sources.contains(e.source())) {
                return false;
            }
            if (isPua != null && e.isPua() != isPua) {
                return false;
            }

            if (generalCategories != null) {
                if (e.generalCategory() == null || !generalCategories.contains(e.generalCategory())) {
                    return false;
                }
            }

            if (mufiRanges != null) {
                if (e.mufiRange() == null || !mufiRanges.contains(e.mufiRange())) {
                    return false;
                }
            }
            if (mufiStatuses != null) {
                if (e.mufiStatus() == null || !mufiStatuses.contains(e.mufiStatus())) {
                    return false;
                }
            }
            if (mufiVersions != null) {
                if (e.mufiVersion() == null || !mufiVersions.contains(e.mufiVersion())) {
                    return false;
                }
            }
            if (deprecated != null && e.source() == CharacterSource.MUFI) {
                if (e.deprecated() != deprecated) {
                    return false;
                }
            }

            return true;
        }
    }

    static final class Index {

        private final List<CharacterEntry> entries;
        private final Map<String, int[]> postings;

        private Index(List<CharacterEntry> entries, Map<String, int[]> postings) {
            this.entries = entries;
            this.postings = postings;
        }

        static Index build(List<CharacterEntry> entries) {
            Map<String, IntList> builders = new HashMap<>(65_536);

            for (int i = 0; i < entries.size(); i++) {
                CharacterEntry e = entries.get(i);
                for (String token : Tokenizer.tokenize(e.description())) {
                    builders.computeIfAbsent(token, k -> new IntList()).add(i);
                }
            }

            Map<String, int[]> postings = new HashMap<>(builders.size() * 2);
            for (Map.Entry<String, IntList> en : builders.entrySet()) {
                postings.put(en.getKey(), en.getValue().toArray());
            }

            return new Index(List.copyOf(entries), postings);
        }

        SearchResult search(List<String> tokens, String rawQuery, int offset, int limit, Filter filter) {
            List<Integer> matchedIds;

            if (tokens.isEmpty()) {
                matchedIds = new ArrayList<>(entries.size());
                for (int i = 0; i < entries.size(); i++) {
                    if (filter.matches(entries.get(i))) {
                        matchedIds.add(i);
                    }
                }
                matchedIds.sort(Comparator.comparingInt(i -> entries.get(i).codePoint()));
            } else {
                int[] candidate = null;

                for (String token : tokens) {
                    int[] ids = postings.get(token);
                    if (ids == null) {
                        candidate = new int[0];
                        break;
                    }
                    candidate = (candidate == null) ? ids : intersectSorted(candidate, ids);
                    if (candidate.length == 0) {
                        break;
                    }
                }

                if (candidate == null || candidate.length == 0) {
                    matchedIds = List.of();
                } else {
                    matchedIds = new ArrayList<>(candidate.length);
                    for (int id : candidate) {
                        CharacterEntry e = entries.get(id);
                        if (filter.matches(e)) {
                            matchedIds.add(id);
                        }
                    }
                    // Entries are pre-sorted by codePoint when building the index; keep that order.
                }
            }

            Map<String, Map<String, Long>> facets = computeFacets(matchedIds);

            int total = matchedIds.size();
            int from = Math.min(offset, total);
            int to = Math.min(from + limit, total);

            List<CharacterEntry> page = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) {
                page.add(entries.get(matchedIds.get(i)));
            }

            return new SearchResult(rawQuery, offset, limit, total, page, facets);
        }

        private Map<String, Map<String, Long>> computeFacets(List<Integer> ids) {
            Map<String, Map<String, Long>> facets = new LinkedHashMap<>();

            Map<String, Long> bySource = new HashMap<>();
            Map<String, Long> byPua = new HashMap<>();
            Map<String, Long> byGc = new HashMap<>();
            Map<String, Long> byMufiRange = new HashMap<>();
            Map<String, Long> byMufiStatus = new HashMap<>();
            Map<String, Long> byMufiVersion = new HashMap<>();
            Map<String, Long> byMufiDeprecated = new HashMap<>();

            for (int id : ids) {
                CharacterEntry e = entries.get(id);

                bySource.merge(e.source().name().toLowerCase(), 1L, (a, b) -> a + b);
                byPua.merge(e.isPua() ? "true" : "false", 1L, (a, b) -> a + b);

                if (e.source() == CharacterSource.UNICODE && e.generalCategory() != null) {
                    byGc.merge(e.generalCategory(), 1L, (a, b) -> a + b);
                }

                if (e.source() == CharacterSource.MUFI) {
                    if (e.mufiRange() != null) byMufiRange.merge(e.mufiRange(), 1L, (a, b) -> a + b);
                    if (e.mufiStatus() != null) byMufiStatus.merge(e.mufiStatus(), 1L, (a, b) -> a + b);
                    if (e.mufiVersion() != null) byMufiVersion.merge(e.mufiVersion(), 1L, (a, b) -> a + b);
                    byMufiDeprecated.merge(e.deprecated() ? "true" : "false", 1L, (a, b) -> a + b);
                }
            }

            facets.put("source", sortFacet(bySource));
            facets.put("isPua", sortFacet(byPua));
            if (!byGc.isEmpty()) facets.put("generalCategory", sortFacet(byGc));
            if (!byMufiRange.isEmpty()) facets.put("mufiRange", sortFacet(byMufiRange));
            if (!byMufiStatus.isEmpty()) facets.put("mufiStatus", sortFacet(byMufiStatus));
            if (!byMufiVersion.isEmpty()) facets.put("mufiVersion", sortFacet(byMufiVersion));
            if (!byMufiDeprecated.isEmpty()) facets.put("mufiDeprecated", sortFacet(byMufiDeprecated));

            return facets;
        }

        private static Map<String, Long> sortFacet(Map<String, Long> in) {
            return in.entrySet().stream()
                    .sorted((a, b) -> {
                        int c = Long.compare(b.getValue(), a.getValue());
                        if (c != 0) return c;
                        return a.getKey().compareTo(b.getKey());
                    })
                    .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
        }

        private static int[] intersectSorted(int[] a, int[] b) {
            int i = 0;
            int j = 0;
            int[] tmp = new int[Math.min(a.length, b.length)];
            int w = 0;
            while (i < a.length && j < b.length) {
                int av = a[i];
                int bv = b[j];
                if (av == bv) {
                    tmp[w++] = av;
                    i++;
                    j++;
                } else if (av < bv) {
                    i++;
                } else {
                    j++;
                }
            }
            return w == tmp.length ? tmp : Arrays.copyOf(tmp, w);
        }
    }

    private static final class Tokenizer {
        static List<String> tokenize(String text) {
            if (text == null) {
                return List.of();
            }

            String s = text.trim();
            if (s.isEmpty()) {
                return List.of();
            }

            ArrayList<String> tokens = new ArrayList<>(8);
            int start = -1;
            for (int i = 0; i < s.length(); i++) {
                char ch = s.charAt(i);
                boolean isTokenChar = Character.isLetterOrDigit(ch);
                if (isTokenChar) {
                    if (start < 0) start = i;
                } else {
                    if (start >= 0) {
                        addToken(tokens, s.substring(start, i));
                        start = -1;
                    }
                }
            }
            if (start >= 0) {
                addToken(tokens, s.substring(start));
            }

            // de-dup while preserving order
            if (tokens.size() <= 1) {
                return tokens;
            }
            LinkedHashSet<String> dedup = new LinkedHashSet<>(tokens);
            return new ArrayList<>(dedup);
        }

        private static void addToken(List<String> out, String raw) {
            if (raw == null) {
                return;
            }
            String t = raw.trim().toUpperCase(Locale.ROOT);
            if (t.isEmpty()) return;
            out.add(t);
        }
    }

    private static final class IntList {
        private int[] data = new int[16];
        private int size = 0;

        void add(int v) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = v;
        }

        int[] toArray() {
            int[] out = Arrays.copyOf(data, size);
            Arrays.sort(out);
            // unique
            int w = 0;
            int prev = Integer.MIN_VALUE;
            for (int v : out) {
                if (v != prev) {
                    out[w++] = v;
                    prev = v;
                }
            }
            return w == out.length ? out : Arrays.copyOf(out, w);
        }
    }
}
