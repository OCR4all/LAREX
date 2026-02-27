package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO for a word, aligned with page4j's Word.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WordDto(
    /** Unique identifier */
    String id,
    /** Coordinates polygon */
    PolygonDto coords,
    /** Text content variants (multiple OCR interpretations) */
    List<TextContentVariantDto> textContentVariants,
    /** Child glyphs */
    List<GlyphDto> glyphs,
    
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
    String language,
    String script,
    String readingDirection,
    String production,
    Double confidence,
    String custom,
    String comments
) {
    /**
     * Get the primary text content.
     */
    public String getText() {
        // First try text content variants
        if (textContentVariants != null && !textContentVariants.isEmpty()) {
            return textContentVariants.stream()
                .filter(v -> v.index() != null)
                .min((a, b) -> a.index().compareTo(b.index()))
                .map(TextContentVariantDto::unicode)
                .orElseGet(() -> textContentVariants.get(0).unicode());
        }
        // Fall back to composing from glyphs
        if (glyphs != null && !glyphs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (GlyphDto glyph : glyphs) {
                String text = glyph.getText();
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return null;
    }
}
