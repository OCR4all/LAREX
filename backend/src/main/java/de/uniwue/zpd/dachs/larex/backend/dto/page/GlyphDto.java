package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO for a glyph (character-level annotation), aligned with page4j's Glyph.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GlyphDto(
    /** Unique identifier */
    String id,
    /** Coordinates polygon */
    PolygonDto coords,
    /** Text content variants (multiple OCR interpretations) */
    List<TextContentVariantDto> textContentVariants,
    
    // Style attributes (from page4j LowLevelTextObject)
    Boolean bold,
    Boolean italic,
    Boolean underlined,
    String underlineStyle,
    Boolean subscript,
    Boolean superscript,
    Boolean strikethrough,
    Boolean smallCaps,
    Boolean letterSpaced,
    
    // Additional attributes
    Boolean ligature,
    Boolean symbol,
    String script,
    String production,
    Double confidence,
    String custom,
    String comments
) {
    /**
     * Get the primary text content (first variant's unicode).
     */
    public String getText() {
        if (textContentVariants != null && !textContentVariants.isEmpty()) {
            // Find variant with lowest index, or first if no indices
            return textContentVariants.stream()
                .filter(v -> v.index() != null)
                .min((a, b) -> a.index().compareTo(b.index()))
                .map(TextContentVariantDto::unicode)
                .orElseGet(() -> textContentVariants.get(0).unicode());
        }
        return null;
    }
}
