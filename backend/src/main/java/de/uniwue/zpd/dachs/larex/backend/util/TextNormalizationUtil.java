package de.uniwue.zpd.dachs.larex.backend.util;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

public final class TextNormalizationUtil {

    private static final Pattern LINE_BREAK_HYPHEN = Pattern.compile("(?<=\\p{L}|\\p{N})[-‐‑‒–—]\\s*\\R\\s*(?=\\p{L}|\\p{N})");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Map<String, String> COMMON_LIGATURES = createLigatureMap();

    public record ReplacementRule(String search, String replacement, boolean regex) {
    }

    public record PreparedReplacementRule(String search,
                                          String replacement,
                                          boolean regex,
                                          Pattern pattern) {
    }

    public record TraceMatch(String key,
                             String label,
                             String description,
                             boolean manual,
                             boolean regex) {
    }

    public record TraceResult(String normalizedText, List<TraceMatch> matchedRules) {
    }

    private TextNormalizationUtil() {
    }

    public static String normalize(String text,
                                   String unicodeNormalization,
                                   boolean collapseWhitespace,
                                   boolean trimText,
                                   boolean dehyphenateLineBreaks,
                                   boolean mapLongSToS,
                                   boolean expandCommonLigatures,
                                   boolean normalizeQuotes,
                                   boolean normalizeDashes,
                                   boolean normalizeEllipsis,
                                   List<ReplacementRule> replacementRules) {
        return normalizeWithTrace(
                text,
                unicodeNormalization,
                collapseWhitespace,
                trimText,
                dehyphenateLineBreaks,
                mapLongSToS,
                expandCommonLigatures,
                normalizeQuotes,
                normalizeDashes,
                normalizeEllipsis,
                replacementRules
        ).normalizedText();
    }

    public static TraceResult normalizeWithTrace(String text,
                                                 String unicodeNormalization,
                                                 boolean collapseWhitespace,
                                                 boolean trimText,
                                                 boolean dehyphenateLineBreaks,
                                                 boolean mapLongSToS,
                                                 boolean expandCommonLigatures,
                                                 boolean normalizeQuotes,
                                                 boolean normalizeDashes,
                                                 boolean normalizeEllipsis,
                                                 List<ReplacementRule> replacementRules) {
        return normalizeWithPreparedTrace(
                text,
                unicodeNormalization,
                collapseWhitespace,
                trimText,
                dehyphenateLineBreaks,
                mapLongSToS,
                expandCommonLigatures,
                normalizeQuotes,
                normalizeDashes,
                normalizeEllipsis,
                prepareReplacementRules(replacementRules)
        );
    }

    public static TraceResult normalizeWithPreparedTrace(String text,
                                                         String unicodeNormalization,
                                                         boolean collapseWhitespace,
                                                         boolean trimText,
                                                         boolean dehyphenateLineBreaks,
                                                         boolean mapLongSToS,
                                                         boolean expandCommonLigatures,
                                                         boolean normalizeQuotes,
                                                         boolean normalizeDashes,
                                                         boolean normalizeEllipsis,
                                                         List<PreparedReplacementRule> replacementRules) {
        if (text == null) {
            return new TraceResult(null, List.of());
        }

        String normalized = text;
        List<TraceMatch> matchedRules = new ArrayList<>();
        if (dehyphenateLineBreaks) {
            normalized = applyTransform(
                    normalized,
                    value -> LINE_BREAK_HYPHEN.matcher(value).replaceAll(""),
                    matchedRules,
                    "preset:dehyphenateLineBreaks",
                    "Dehyphenate line breaks",
                    "Remove hyphenated line-break joins",
                    false,
                    false
            );
        }

        if (shouldApplyUnicodeNormalization(unicodeNormalization)) {
            String normalizationForm = (unicodeNormalization == null || unicodeNormalization.isBlank())
                    ? "NFC"
                    : unicodeNormalization.toUpperCase();
            normalized = applyTransform(
                    normalized,
                    value -> Normalizer.normalize(value, toNormalizerForm(unicodeNormalization)),
                    matchedRules,
                    "preset:unicodeNormalization",
                    "Unicode normalization",
                    normalizationForm,
                    false,
                    false
            );
        }

        if (mapLongSToS) {
            normalized = applyTransform(
                    normalized,
                    value -> value.replace('ſ', 's').replace('ẜ', 's'),
                    matchedRules,
                    "preset:mapLongSToS",
                    "Map long s to s",
                    "Replace historical long-s characters",
                    false,
                    false
            );
        }

        if (expandCommonLigatures) {
            normalized = applyTransform(
                    normalized,
                    value -> {
                        String updated = value;
                        for (Map.Entry<String, String> entry : COMMON_LIGATURES.entrySet()) {
                            updated = updated.replace(entry.getKey(), entry.getValue());
                        }
                        return updated;
                    },
                    matchedRules,
                    "preset:expandCommonLigatures",
                    "Expand common ligatures",
                    "Replace common typographic ligatures",
                    false,
                    false
            );
        }

        if (normalizeQuotes) {
            normalized = applyTransform(
                    normalized,
                    value -> value
                            .replace('“', '"')
                            .replace('”', '"')
                            .replace('„', '"')
                            .replace('‟', '"')
                            .replace('«', '"')
                            .replace('»', '"')
                            .replace('‘', '\'')
                            .replace('’', '\'')
                            .replace('‚', '\'')
                            .replace('‛', '\''),
                    matchedRules,
                    "preset:normalizeQuotes",
                    "Normalize quotes",
                    "Convert curly or angled quotation marks",
                    false,
                    false
            );
        }

        if (normalizeDashes) {
            normalized = applyTransform(
                    normalized,
                    value -> value
                            .replace('‐', '-')
                            .replace('‑', '-')
                            .replace('‒', '-')
                            .replace('–', '-')
                            .replace('—', '-'),
                    matchedRules,
                    "preset:normalizeDashes",
                    "Normalize dashes",
                    "Convert dash variants to hyphen-minus",
                    false,
                    false
            );
        }

        if (normalizeEllipsis) {
            normalized = applyTransform(
                    normalized,
                    value -> value.replace("…", "..."),
                    matchedRules,
                    "preset:normalizeEllipsis",
                    "Normalize ellipsis",
                    "Convert ellipsis character to three periods",
                    false,
                    false
            );
        }

        if (replacementRules != null) {
            for (int index = 0; index < replacementRules.size(); index++) {
                PreparedReplacementRule rule = replacementRules.get(index);
                if (rule == null || rule.search() == null || rule.search().isBlank()) {
                    continue;
                }
                String replacement = rule.replacement() == null ? "" : rule.replacement();
                final int ruleIndex = index + 1;
                normalized = applyTransform(
                        normalized,
                        value -> rule.regex()
                                ? rule.pattern().matcher(value).replaceAll(replacement)
                                : value.replace(rule.search(), replacement),
                        matchedRules,
                        "manual:" + ruleIndex,
                        rule.regex() ? "Regex rule " + ruleIndex : "Replacement rule " + ruleIndex,
                        rule.search() + " -> " + replacement,
                        true,
                        rule.regex()
                );
            }
        }

        if (collapseWhitespace) {
            normalized = applyTransform(
                    normalized,
                    value -> WHITESPACE.matcher(value).replaceAll(" "),
                    matchedRules,
                    "preset:collapseWhitespace",
                    "Collapse whitespace",
                    "Collapse repeated whitespace to single spaces",
                    false,
                    false
            );
        }

        if (trimText) {
            normalized = applyTransform(
                    normalized,
                    String::trim,
                    matchedRules,
                    "preset:trimText",
                    "Trim text",
                    "Remove leading and trailing whitespace",
                    false,
                    false
            );
        }

        return new TraceResult(normalized, List.copyOf(matchedRules));
    }

    public static List<ReplacementRule> normalizeReplacementRules(List<ReplacementRule> replacementRules) {
        if (replacementRules == null || replacementRules.isEmpty()) {
            return List.of();
        }
        List<ReplacementRule> normalizedRules = new ArrayList<>(replacementRules.size());
        for (ReplacementRule rule : replacementRules) {
            if (rule == null || rule.search() == null || rule.search().isBlank()) {
                continue;
            }
            normalizedRules.add(new ReplacementRule(rule.search().trim(), rule.replacement(), rule.regex()));
        }
        return normalizedRules;
    }

    public static List<PreparedReplacementRule> prepareReplacementRules(List<ReplacementRule> replacementRules) {
        if (replacementRules == null || replacementRules.isEmpty()) {
            return List.of();
        }

        List<PreparedReplacementRule> preparedRules = new ArrayList<>(replacementRules.size());
        for (ReplacementRule rule : replacementRules) {
            if (rule == null || rule.search() == null || rule.search().isBlank()) {
                continue;
            }
            preparedRules.add(new PreparedReplacementRule(
                    rule.search(),
                    rule.replacement(),
                    rule.regex(),
                    rule.regex() ? Pattern.compile(rule.search()) : null
            ));
        }
        return preparedRules;
    }

    public static Normalizer.Form toNormalizerForm(String normalization) {
        if ("NFD".equalsIgnoreCase(normalization)) {
            return Normalizer.Form.NFD;
        }
        if ("NFKC".equalsIgnoreCase(normalization)) {
            return Normalizer.Form.NFKC;
        }
        if ("NFKD".equalsIgnoreCase(normalization)) {
            return Normalizer.Form.NFKD;
        }
        return Normalizer.Form.NFC;
    }

    public static boolean shouldApplyUnicodeNormalization(String normalization) {
        return normalization == null || !"NONE".equalsIgnoreCase(normalization.trim());
    }

    private static String applyTransform(String input,
                                         UnaryOperator<String> transform,
                                         List<TraceMatch> matchedRules,
                                         String key,
                                         String label,
                                         String description,
                                         boolean manual,
                                         boolean regex) {
        String output = transform.apply(input);
        if (!Objects.equals(input, output)) {
            matchedRules.add(new TraceMatch(key, label, description, manual, regex));
        }
        return output;
    }

    private static Map<String, String> createLigatureMap() {
        Map<String, String> replacements = new LinkedHashMap<>();
        replacements.put("ﬀ", "ff");
        replacements.put("ﬁ", "fi");
        replacements.put("ﬂ", "fl");
        replacements.put("ﬃ", "ffi");
        replacements.put("ﬄ", "ffl");
        replacements.put("ﬅ", "st");
        replacements.put("ﬆ", "st");
        return replacements;
    }
}
