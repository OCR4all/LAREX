package de.uniwue.zpd.dachs.larex.backend.dto.page.text;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.style.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.*;

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
    /** Alternative images */
    List<AlternativeImageDto> alternativeImages,
    /** PAGE labels */
    List<LabelsDto> labels,
    /** User-defined attributes */
    UserDefinedDto userDefined,
    /** Text style attributes */
    TextStyleDto textStyle,
    /** Grapheme container */
    GraphemesDto graphemes,
    
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
    public GlyphDto(
        String id,
        PolygonDto coords,
        List<TextContentVariantDto> textContentVariants,
        Boolean bold,
        Boolean italic,
        Boolean underlined,
        String underlineStyle,
        Boolean subscript,
        Boolean superscript,
        Boolean strikethrough,
        Boolean smallCaps,
        Boolean letterSpaced,
        Boolean ligature,
        Boolean symbol,
        String script,
        String production,
        Double confidence,
        String custom,
        String comments
    ) {
        this(
            id,
            coords,
            textContentVariants,
            null,
            null,
            null,
            null,
            null,
            bold,
            italic,
            underlined,
            underlineStyle,
            subscript,
            superscript,
            strikethrough,
            smallCaps,
            letterSpaced,
            ligature,
            symbol,
            script,
            production,
            confidence,
            custom,
            comments
        );
    }

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
