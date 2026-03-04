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

/**
 * DTO for a text content variant, aligned with page4j's TextContentVariant.
 * PAGE XML TextEquiv element - represents one text interpretation/variant.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextContentVariantDto(
    /** The Unicode text content */
    String unicode,
    /** Plain text version (without formatting) */
    String plainText,
    /** Confidence score (0.0 to 1.0) */
    Double confidence,
    /** Index for ordering multiple variants (lower = higher priority) */
    Integer index,
    /** Data type identifier */
    String dataType,
    /** Data type details */
    String dataTypeDetails,
    /** Comments about this text variant */
    String comments
) {
    /**
     * Convenience constructor for simple text content.
     */
    public TextContentVariantDto(String unicode) {
        this(unicode, null, null, null, null, null, null);
    }

    /**
     * Convenience constructor with confidence.
     */
    public TextContentVariantDto(String unicode, Double confidence) {
        this(unicode, null, confidence, null, null, null, null);
    }

    /**
     * Convenience constructor with index and confidence.
     */
    public TextContentVariantDto(String unicode, Double confidence, Integer index) {
        this(unicode, null, confidence, index, null, null, null);
    }
}
