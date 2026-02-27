package de.uniwue.zpd.dachs.larex.backend.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class TextIndexDefaultsUtil {

    public static final int DEFAULT_GT_INDEX = 0;
    public static final List<Integer> DEFAULT_RECOGNITION_INDICES = List.of(1);
    public static final int UNINDEXED_RECOGNITION_SENTINEL = -1;

    private TextIndexDefaultsUtil() {}

    public record TextIndexDefaults(int gtIndex, List<Integer> recognitionIndices) {}

    public static int effectiveGtIndex(Integer value) {
        if (value == null) return DEFAULT_GT_INDEX;
        if (value < 0) return DEFAULT_GT_INDEX;
        return value;
    }

    public static List<Integer> effectiveRecognitionIndices(List<Integer> value) {
        try {
            return normalizeRecognitionIndices(value);
        } catch (IllegalArgumentException ignored) {
            return DEFAULT_RECOGNITION_INDICES;
        }
    }

    public static TextIndexDefaults resolve(
            Integer requestedGtIndex,
            List<Integer> requestedRecognitionIndices,
            Integer fallbackGtIndex,
            List<Integer> fallbackRecognitionIndices
    ) {
        int gtIndex = requestedGtIndex != null
                ? normalizeGtIndex(requestedGtIndex)
                : effectiveGtIndex(fallbackGtIndex);
        List<Integer> recognitionIndices = requestedRecognitionIndices != null
                ? normalizeRecognitionIndices(requestedRecognitionIndices)
                : effectiveRecognitionIndices(fallbackRecognitionIndices);

        validateNoOverlap(gtIndex, recognitionIndices);
        return new TextIndexDefaults(gtIndex, recognitionIndices);
    }

    public static int normalizeGtIndex(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("Default Ground Truth Index is required.");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Default Ground Truth Index must be >= 0.");
        }
        return value;
    }

    public static List<Integer> normalizeRecognitionIndices(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("At least one Recognition Index is required.");
        }

        Set<Integer> normalized = new LinkedHashSet<>();
        for (Integer value : values) {
            if (value == null) {
                throw new IllegalArgumentException("Recognition Indices cannot contain null values.");
            }
            if (value < 0 && value != UNINDEXED_RECOGNITION_SENTINEL) {
                throw new IllegalArgumentException("Recognition Indices must be >= 0 (or -1 for Undefined).");
            }
            normalized.add(value);
        }

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one Recognition Index is required.");
        }

        return normalized.stream()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public static void validateNoOverlap(int gtIndex, List<Integer> recognitionIndices) {
        if (recognitionIndices.contains(gtIndex)) {
            throw new IllegalArgumentException("Ground Truth Index must not be part of Recognition Indices.");
        }
    }

    public static String toCsv(List<Integer> indices) {
        return effectiveRecognitionIndices(indices).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public static List<Integer> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return DEFAULT_RECOGNITION_INDICES;
        }

        try {
            List<Integer> values = List.of(csv.split(",")).stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .map(Integer::parseInt)
                    .toList();
            return effectiveRecognitionIndices(values);
        } catch (RuntimeException ignored) {
            return DEFAULT_RECOGNITION_INDICES;
        }
    }

    public static boolean equalsDefaults(int leftGt, List<Integer> leftRec, int rightGt, List<Integer> rightRec) {
        return leftGt == rightGt && Objects.equals(effectiveRecognitionIndices(leftRec), effectiveRecognitionIndices(rightRec));
    }
}
