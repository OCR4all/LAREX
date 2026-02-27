package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO for a text line, aligned with page4j's TextLine.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextLineDto(
    /** Unique identifier */
    String id,
    /** Coordinates polygon */
    PolygonDto coords,
    /** Baseline polygon (typically a polyline) */
    PolygonDto baseline,
    /** Text content variants (multiple OCR interpretations) */
    List<TextContentVariantDto> textContentVariants,
    /** Child words */
    List<WordDto> words,
    
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
    String primaryLanguage,
    String primaryScript,
    String secondaryScript,
    String readingDirection,
    String production,
    Double confidence,
    Integer index,
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
        // Fall back to composing from words
        if (words != null && !words.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) sb.append(" ");
                String text = words.get(i).getText();
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return null;
    }
}
