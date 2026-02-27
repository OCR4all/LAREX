package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO for a region, aligned with page4j's Region/TextRegion.
 * Represents any PAGE XML region type (TextRegion, ImageRegion, etc.).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegionDto(
    /** Unique identifier */
    String id,
    /** Region kind (TextRegion, ImageRegion, etc.) */
    RegionKind kind,
    /** Coordinates polygon */
    PolygonDto coords,
    
    // For TextRegion: text lines and content
    /** Child text lines (only for TextRegion) */
    List<TextLineDto> textLines,
    /** Text content variants (only for TextRegion) */
    List<TextContentVariantDto> textContentVariants,
    
    // TextRegion-specific attributes
    /** TextRegion type attribute (paragraph, heading, caption, etc.) */
    String type,
    /** Orientation in degrees */
    Double orientation,
    /** Text color */
    String textColour,
    /** Background color */
    String bgColour,
    /** Reverse video flag */
    Boolean reverseVideo,
    /** Font size */
    Double fontSize,
    /** Font family */
    String fontFamily,
    /** Leading (line spacing) */
    Integer leading,
    /** Kerning */
    Integer kerning,
    /** Reading direction */
    String readingDirection,
    /** Reading orientation */
    Double readingOrientation,
    /** Text line order */
    String textLineOrder,
    /** Indented flag */
    Boolean indented,
    /** Primary language */
    String primaryLanguage,
    /** Secondary language */
    String secondaryLanguage,
    /** Primary script */
    String primaryScript,
    /** Secondary script */
    String secondaryScript,
    /** Production type */
    String production,
    
    // Nested regions
    /** Child/nested regions */
    List<RegionDto> nestedRegions,
    
    // Common attributes
    /** Confidence score */
    Double confidence,
    /** Custom attribute (PAGE XML @custom) */
    String custom,
    /** Comments */
    String comments,
    /** Continuation flag */
    Boolean continuation,
    
    // TODO: Labels integration - map page4j Labels to LabelSet
    /** Label IDs from LabelSet (for bi-directional mapping) */
    List<String> labelIds
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
        // Fall back to composing from text lines
        if (textLines != null && !textLines.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < textLines.size(); i++) {
                if (i > 0) sb.append("\n");
                String text = textLines.get(i).getText();
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.toString();
        }
        return null;
    }
    
    /**
     * Check if this is a TextRegion.
     */
    public boolean isTextRegion() {
        return kind == RegionKind.TextRegion;
    }
}
