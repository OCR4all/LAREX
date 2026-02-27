package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * DTO for a full PAGE document, aligned with page4j's Page.
 * This is the main DTO returned by the annotation API.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageDto(
    // Image information
    /** Image filename */
    String imageFilename,
    /** Image width in pixels */
    int imageWidth,
    /** Image height in pixels */
    int imageHeight,
    /** Image X resolution */
    Integer imageXResolution,
    /** Image Y resolution */
    Integer imageYResolution,
    /** Resolution unit (PPI, PPCM, etc.) */
    String imageResolutionUnit,
    
    // Metadata
    /** Document metadata */
    MetadataDto metadata,
    
    // Page-level attributes
    /** Ground truth/storage ID */
    String pcGtsId,
    /** Page type (front-cover, content, etc.) */
    String type,
    /** Custom attribute */
    String custom,
    /** Orientation in degrees */
    Double orientation,
    /** Primary language */
    String primaryLanguage,
    /** Secondary language */
    String secondaryLanguage,
    /** Primary script */
    String primaryScript,
    /** Secondary script */
    String secondaryScript,
    /** Reading direction */
    String readingDirection,
    /** Text line order */
    String textLineOrder,
    /** Confidence */
    Double confidence,
    
    // Page structure
    /** Page border */
    PolygonDto border,
    /** Print space */
    PolygonDto printSpace,
    /** Regions on this page */
    List<RegionDto> regions,
    /** Reading order */
    ReadingOrderDto readingOrder,
    
    // Format version
    /** PAGE XML format version used */
    String formatVersion,
    
    // TODO: Labels integration - map page4j Labels to LabelSet
    /** Label IDs from LabelSet applied at page level */
    List<String> labelIds
) {
    /**
     * Get page dimensions.
     */
    public record Dimensions(int width, int height) {}

    public Dimensions getDimensions() {
        return new Dimensions(imageWidth, imageHeight);
    }

    /**
     * Get total region count.
     */
    public int getRegionCount() {
        return regions != null ? regions.size() : 0;
    }

    /**
     * Get total text line count across all text regions.
     */
    public int getTextLineCount() {
        if (regions == null) return 0;
        return regions.stream()
            .filter(r -> r.textLines() != null)
            .mapToInt(r -> r.textLines().size())
            .sum();
    }
}
