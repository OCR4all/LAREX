package de.uniwue.zpd.dachs.larex.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextIndexDefaultsUtilTest {

    @Test
    void resolve_acceptsUnindexedGroundTruth() {
        var resolved = TextIndexDefaultsUtil.resolve(-1, List.of(1, 0), 0, List.of(1));

        assertEquals(-1, resolved.gtIndex());
        assertEquals(List.of(0, 1), resolved.recognitionIndices());
    }

    @Test
    void resolve_rejectsUnindexedForGroundTruthAndRecognition() {
        assertThrows(IllegalArgumentException.class,
                () -> TextIndexDefaultsUtil.resolve(-1, List.of(-1, 1), 0, List.of(1)));
    }

    @Test
    void resolve_rejectsOtherNegativeGroundTruth() {
        assertThrows(IllegalArgumentException.class,
                () -> TextIndexDefaultsUtil.resolve(-2, List.of(1), 0, List.of(1)));
    }

    @Test
    void effectiveGtIndex_keepsUnindexedAndFallsBackForInvalidValues() {
        assertEquals(-1, TextIndexDefaultsUtil.effectiveGtIndex(-1));
        assertEquals(0, TextIndexDefaultsUtil.effectiveGtIndex(-5));
        assertEquals(0, TextIndexDefaultsUtil.effectiveGtIndex(null));
        assertEquals(2, TextIndexDefaultsUtil.effectiveGtIndex(2));
    }

    @Test
    void matchesTextIndex_mapsMissingIndexToUnindexed() {
        assertTrue(TextIndexDefaultsUtil.matchesTextIndex(null, -1));
        assertTrue(TextIndexDefaultsUtil.matchesTextIndex(3, 3));
        assertFalse(TextIndexDefaultsUtil.matchesTextIndex(null, 0));
        assertFalse(TextIndexDefaultsUtil.matchesTextIndex(0, -1));
    }
}
