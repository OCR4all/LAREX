package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.basic.variable.BooleanValue;
import com.maxnth.page4j.basic.variable.DoubleValue;
import com.maxnth.page4j.basic.variable.IntegerValue;
import com.maxnth.page4j.basic.variable.StringValue;
import com.maxnth.page4j.basic.variable.VariableMap;
import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames;
import com.maxnth.page4j.dla.page.layout.PageLayout;
import com.maxnth.page4j.dla.page.layout.logical.Group;
import com.maxnth.page4j.dla.page.layout.logical.GroupMember;
import com.maxnth.page4j.dla.page.layout.logical.ReadingOrder;
import com.maxnth.page4j.dla.page.layout.logical.RegionRef;
import com.maxnth.page4j.dla.page.layout.physical.AttributeContainer;
import com.maxnth.page4j.dla.page.layout.physical.Region;
import com.maxnth.page4j.dla.page.layout.physical.shared.RegionType;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextContainer;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextObject;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContent;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Glyph;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextLine;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextRegion;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Word;
import com.maxnth.page4j.dla.page.metadata.MetaData;
import com.maxnth.page4j.maths.geometry.Point;
import com.maxnth.page4j.maths.geometry.Polygon;

import de.uniwue.zpd.dachs.larex.backend.dto.page.*;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.parser.PageXmlPresenceIndex;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps page4j Page objects to PageDto DTOs.
 * This mapper extracts all information from the page4j model and converts it to
 * a format suitable for JSON serialization.
 */
@Component
public class Page4jToDtoMapper {

    // Image dimensions for coordinate conversion (set during toDto call)
    private int imageWidth;
    private int imageHeight;
    private PageXmlPresenceIndex presenceIndex;

    /**
     * Convert empty strings to null to avoid XML validation errors when saving.
     * PAGE XML schema doesn't allow empty strings for enumeration attributes.
     */
    private static String nullIfEmpty(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Convert a page4j Page to a PageDto.
     */
    public PageDto toDto(Page page) {
        return toDto(page, null);
    }

    /**
     * Convert a page4j Page to a PageDto with source XML presence information.
     */
    public PageDto toDto(Page page, PageXmlPresenceIndex presenceIndex) {
        if (page == null) {
            return null;
        }

        this.presenceIndex = presenceIndex;
        PageLayout layout = page.getLayout();

        // Store image dimensions for coordinate conversion
        this.imageWidth = layout.getWidth();
        this.imageHeight = layout.getHeight();

        return new PageDto(
            page.getImageFilename(),
            layout.getWidth(),
            layout.getHeight(),
            getIntegerAttr(page, DefaultXmlNames.ATTR_imageXResolution),
            getIntegerAttr(page, "imageYResolution"),
            getStringAttr(page, "imageResolutionUnit"),
            toMetadataDto(page.getMetaData()),
            page.getGtsId() != null ? page.getGtsId().toString() : null,
            getPageType(page),
            getCustomAttribute(page),
            getOrientation(page),
            getPrimaryLanguage(page),
            getSecondaryLanguage(page),
            getPrimaryScript(page),
            getSecondaryScript(page),
            getReadingDirection(page),
            getTextLineOrder(page),
            getConfidence(page),
            toBorderPolygonDto(layout),
            toPrintSpacePolygonDto(layout),
            toRegionDtos(layout),
            toReadingOrderDto(layout.getReadingOrder()),
            page.getFormatVersion() != null ? page.getFormatVersion().toString() : null,
            // TODO: Map page4j Labels to LabelSet IDs
            mapLabelsToLabelIds(page)
        );
    }

    private MetadataDto toMetadataDto(MetaData metaData) {
        if (metaData == null) {
            return null;
        }
        String creator = isMetadataFieldPresent("creator") ? nullIfEmpty(metaData.getCreator()) : null;
        String created = isMetadataFieldPresent("created") ? nullIfEmpty(metaData.getFormattedCreationTime()) : null;
        String lastChange = isMetadataFieldPresent("lastChange") ? nullIfEmpty(metaData.getFormattedLastModificationTime()) : null;
        String comments = isMetadataFieldPresent("comments") ? nullIfEmpty(metaData.getComments()) : null;
        String externalRef = isMetadataFieldPresent("externalRef") ? nullIfEmpty(metaData.getExternalRef()) : null;
        if (creator == null && created == null && lastChange == null && comments == null && externalRef == null) {
            return null;
        }
        return new MetadataDto(creator, created, lastChange, comments, externalRef);
    }

    private PolygonDto toBorderPolygonDto(PageLayout layout) {
        if (layout.getBorder() == null || layout.getBorder().getCoords() == null) {
            return null;
        }
        return toPolygonDto(layout.getBorder().getCoords());
    }

    private PolygonDto toPrintSpacePolygonDto(PageLayout layout) {
        if (layout.getPrintSpace() == null || layout.getPrintSpace().getCoords() == null) {
            return null;
        }
        return toPolygonDto(layout.getPrintSpace().getCoords());
    }

    private List<RegionDto> toRegionDtos(PageLayout layout) {
        List<RegionDto> regions = new ArrayList<>();
        for (int i = 0; i < layout.getRegionCount(); i++) {
            Region region = layout.getRegion(i);
            regions.add(toRegionDto(region));
        }
        return regions;
    }

    private RegionDto toRegionDto(Region region) {
        if (region == null) {
            return null;
        }

        RegionKind kind = RegionKind.fromPage4jName(region.getType().getName());
        List<TextLineDto> textLines = null;
        List<TextContentVariantDto> textContentVariants = null;

        // Extract TextRegion-specific data
        String type = null;
        Double orientation = null;
        String textColour = null;
        String bgColour = null;
        Boolean reverseVideo = null;
        Double fontSize = null;
        String fontFamily = null; // Not supported in page4j - always null
        Integer leading = null;
        Integer kerning = null;
        String readingDirection = null;
        Double readingOrientation = null;
        String textLineOrder = null;
        Boolean indented = null;
        String primaryLanguage = null;
        String secondaryLanguage = null;
        String primaryScript = null;
        String secondaryScript = null;
        String production = null; // Not supported in page4j - always null
        Boolean continuation = getBooleanAttr(region, DefaultXmlNames.ATTR_continuation);

        if (region instanceof TextRegion textRegion) {
            textLines = toTextLineDtos(textRegion);
            textContentVariants = toTextContentVariantDtos(textRegion);
            type = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_type));
            orientation = getDoubleAttr(textRegion, DefaultXmlNames.ATTR_orientation);
            textColour = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_textColour));
            bgColour = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_bgColour));
            reverseVideo = getBooleanAttr(textRegion, DefaultXmlNames.ATTR_reverseVideo);
            fontSize = getDoubleAttr(textRegion, DefaultXmlNames.ATTR_fontSize);
            // fontFamily not supported in page4j
            leading = getIntegerAttr(textRegion, DefaultXmlNames.ATTR_leading);
            kerning = getIntegerAttr(textRegion, DefaultXmlNames.ATTR_kerning);
            readingDirection = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_readingDirection));
            readingOrientation = getDoubleAttr(textRegion, DefaultXmlNames.ATTR_readingOrientation);
            textLineOrder = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_textLineOrder));
            indented = getBooleanAttr(textRegion, DefaultXmlNames.ATTR_indented);
            primaryLanguage = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_primaryLanguage));
            secondaryLanguage = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_secondaryLanguage));
            primaryScript = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_primaryScript));
            secondaryScript = nullIfEmpty(getStringAttr(textRegion, DefaultXmlNames.ATTR_secondaryScript));
            production = nullIfEmpty(getStringAttr(textRegion, "production"));
        }

        // Handle nested regions
        List<RegionDto> nestedRegions = null;
        if (region.getRegionCount() > 0) {
            nestedRegions = new ArrayList<>();
            for (int i = 0; i < region.getRegionCount(); i++) {
                nestedRegions.add(toRegionDto(region.getRegion(i)));
            }
        }

        return new RegionDto(
            region.getId() != null ? region.getId().toString() : null,
            kind,
            toPolygonDto(region.getCoords()),
            textLines,
            textContentVariants,
            type,
            orientation,
            textColour,
            bgColour,
            reverseVideo,
            fontSize,
            fontFamily,
            leading,
            kerning,
            readingDirection,
            readingOrientation,
            textLineOrder,
            indented,
            primaryLanguage,
            secondaryLanguage,
            primaryScript,
            secondaryScript,
            production,
            nestedRegions,
            getRegionConfidence(region),
            getCustomFromRegion(region),
            getCommentsFromRegion(region),
            continuation,
            // TODO: Map page4j Labels to LabelSet IDs
            mapRegionLabelsToLabelIds(region)
        );
    }

    private List<TextLineDto> toTextLineDtos(TextRegion textRegion) {
        if (!textRegion.hasTextObjects()) {
            return null;
        }

        List<TextLineDto> lines = new ArrayList<>();
        for (int i = 0; i < textRegion.getTextObjectCount(); i++) {
            LowLevelTextObject obj = textRegion.getTextObject(i);
            if (obj instanceof TextLine textLine) {
                lines.add(toTextLineDto(textLine));
            }
        }
        return lines.isEmpty() ? null : lines;
    }

    private TextLineDto toTextLineDto(TextLine textLine) {
        return new TextLineDto(
            textLine.getId() != null ? textLine.getId().toString() : null,
            toPolygonDto(textLine.getCoords()),
            toPolygonDto(textLine.getBaseline()),
            toTextContentVariantDtos(textLine),
            toWordDtos(textLine),
            getBooleanAttr(textLine, "bold"),
            getBooleanAttr(textLine, "italic"),
            getBooleanAttr(textLine, "underlined"),
            nullIfEmpty(getStringAttr(textLine, "underlineStyle")),
            getBooleanAttr(textLine, "subscript"),
            getBooleanAttr(textLine, "superscript"),
            getBooleanAttr(textLine, "strikethrough"),
            getBooleanAttr(textLine, "smallCaps"),
            getBooleanAttr(textLine, "letterSpaced"),
            nullIfEmpty(getStringAttr(textLine, DefaultXmlNames.ATTR_primaryLanguage)),
            nullIfEmpty(getStringAttr(textLine, "primaryScript")),
            nullIfEmpty(getStringAttr(textLine, "secondaryScript")),
            nullIfEmpty(getStringAttr(textLine, "readingDirection")),
            nullIfEmpty(getStringAttr(textLine, "production")),
            getDoubleAttr(textLine, DefaultXmlNames.ATTR_conf),
            getIntegerAttr(textLine, "index"),
            nullIfEmpty(getStringAttr(textLine, "custom")),
            nullIfEmpty(getStringAttr(textLine, "comments"))
        );
    }

    private List<WordDto> toWordDtos(TextLine textLine) {
        if (!textLine.hasTextObjects()) {
            return null;
        }

        List<WordDto> words = new ArrayList<>();
        for (int i = 0; i < textLine.getTextObjectCount(); i++) {
            LowLevelTextObject obj = textLine.getTextObject(i);
            if (obj instanceof Word word) {
                words.add(toWordDto(word));
            }
        }
        return words.isEmpty() ? null : words;
    }

    private WordDto toWordDto(Word word) {
        return new WordDto(
            word.getId() != null ? word.getId().toString() : null,
            toPolygonDto(word.getCoords()),
            toTextContentVariantDtos(word),
            toGlyphDtos(word),
            getBooleanAttr(word, "bold"),
            getBooleanAttr(word, "italic"),
            getBooleanAttr(word, "underlined"),
            nullIfEmpty(getStringAttr(word, "underlineStyle")),
            getBooleanAttr(word, "subscript"),
            getBooleanAttr(word, "superscript"),
            getBooleanAttr(word, "strikethrough"),
            getBooleanAttr(word, "smallCaps"),
            getBooleanAttr(word, "letterSpaced"),
            nullIfEmpty(getStringAttr(word, DefaultXmlNames.ATTR_language)),
            nullIfEmpty(getStringAttr(word, "script")),
            nullIfEmpty(getStringAttr(word, "readingDirection")),
            nullIfEmpty(getStringAttr(word, "production")),
            getDoubleAttr(word, DefaultXmlNames.ATTR_conf),
            nullIfEmpty(getStringAttr(word, "custom")),
            nullIfEmpty(getStringAttr(word, "comments"))
        );
    }

    private List<GlyphDto> toGlyphDtos(Word word) {
        if (!word.hasTextObjects()) {
            return null;
        }

        List<GlyphDto> glyphs = new ArrayList<>();
        for (int i = 0; i < word.getTextObjectCount(); i++) {
            LowLevelTextObject obj = word.getTextObject(i);
            if (obj instanceof Glyph glyph) {
                glyphs.add(toGlyphDto(glyph));
            }
        }
        return glyphs.isEmpty() ? null : glyphs;
    }

    private GlyphDto toGlyphDto(Glyph glyph) {
        return new GlyphDto(
            glyph.getId() != null ? glyph.getId().toString() : null,
            toPolygonDto(glyph.getCoords()),
            toTextContentVariantDtos(glyph),
            getBooleanAttr(glyph, "bold"),
            getBooleanAttr(glyph, "italic"),
            getBooleanAttr(glyph, "underlined"),
            nullIfEmpty(getStringAttr(glyph, "underlineStyle")),
            getBooleanAttr(glyph, "subscript"),
            getBooleanAttr(glyph, "superscript"),
            getBooleanAttr(glyph, "strikethrough"),
            getBooleanAttr(glyph, "smallCaps"),
            getBooleanAttr(glyph, "letterSpaced"),
            getBooleanAttr(glyph, "ligature"),
            getBooleanAttr(glyph, "symbol"),
            nullIfEmpty(getStringAttr(glyph, "script")),
            nullIfEmpty(getStringAttr(glyph, "production")),
            getDoubleAttr(glyph, DefaultXmlNames.ATTR_conf),
            nullIfEmpty(getStringAttr(glyph, "custom")),
            nullIfEmpty(getStringAttr(glyph, "comments"))
        );
    }

    private List<TextContentVariantDto> toTextContentVariantDtos(LowLevelTextObject textObject) {
        int count = textObject.getTextContentVariantCount();
        if (count == 0) {
            return null;
        }

        String ownerId = textObject.getId() != null ? textObject.getId().toString() : null;
        List<TextContentVariantDto> variants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextContent tc = textObject.getTextContentVariant(i);
            variants.add(new TextContentVariantDto(
                nullIfEmpty(tc.getText()),
                nullIfEmpty(tc.getPlainText()),
                getTextEquivConfidence(tc, ownerId, i),
                getIndex(tc),
                nullIfEmpty(tc.getDataType()),
                nullIfEmpty(tc.getDataTypeDetails()),
                nullIfEmpty(tc.getComments())
            ));
        }
        return variants;
    }

    private List<TextContentVariantDto> toTextContentVariantDtos(TextRegion textRegion) {
        int count = textRegion.getTextContentVariantCount();
        if (count == 0) {
            return null;
        }

        String ownerId = textRegion.getId() != null ? textRegion.getId().toString() : null;
        List<TextContentVariantDto> variants = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TextContent tc = textRegion.getTextContentVariant(i);
            variants.add(new TextContentVariantDto(
                nullIfEmpty(tc.getText()),
                nullIfEmpty(tc.getPlainText()),
                getTextEquivConfidence(tc, ownerId, i),
                getIndex(tc),
                nullIfEmpty(tc.getDataType()),
                nullIfEmpty(tc.getDataTypeDetails()),
                nullIfEmpty(tc.getComments())
            ));
        }
        return variants;
    }

    private Double getTextEquivConfidence(TextContent textContent, String ownerId, int textEquivPosition) {
        if (presenceIndex != null && !presenceIndex.hasTextEquivConfidenceForElementId(ownerId, textEquivPosition)) {
            return null;
        }
        return getDoubleAttr(textContent, DefaultXmlNames.ATTR_conf);
    }

    private PolygonDto toPolygonDto(Polygon polygon) {
        if (polygon == null || polygon.getSize() == 0) {
            return null;
        }

        List<PointDto> points = new ArrayList<>();
        for (int i = 0; i < polygon.getSize(); i++) {
            Point p = polygon.getPoint(i);
            // Convert pixel coordinates to world coordinates for frontend rendering
            double worldX = CoordinateUtils.pixelToWorldX(p.x, imageWidth);
            double worldY = CoordinateUtils.pixelToWorldY(p.y, imageHeight);
            points.add(new PointDto(worldX, worldY));
        }
        return new PolygonDto(points, polygon.getConfidence());
    }

    private ReadingOrderDto toReadingOrderDto(ReadingOrder readingOrder) {
        if (readingOrder == null) {
            return null;
        }

        return new ReadingOrderDto(
            toGroupDto(readingOrder.getRoot()),
            readingOrder.getConfidence()
        );
    }

    private ReadingOrderDto.GroupDto toGroupDto(Group group) {
        if (group == null) {
            return null;
        }

        List<ReadingOrderDto.GroupMemberDto> members = new ArrayList<>();
        for (int i = 0; i < group.getSize(); i++) {
            GroupMember member = group.getMember(i);
            if (member instanceof RegionRef regionRef) {
                members.add(new ReadingOrderDto.RegionRefDto(
                    null, // RegionRef doesn't have its own ID
                    regionRef.getRegionId() != null ? regionRef.getRegionId().toString() : null,
                    null // index
                ));
            } else if (member instanceof Group childGroup) {
                members.add(new ReadingOrderDto.NestedGroupDto(toGroupDto(childGroup)));
            }
        }

        return new ReadingOrderDto.GroupDto(
            group.getId() != null ? group.getId().toString() : null,
            group.isOrdered(),
            group.getCaption(),
            group.getRegionRef() != null ? group.getRegionRef().toString() : null,
            members,
            getStringAttr(group, DefaultXmlNames.ATTR_custom),
            getStringAttr(group, DefaultXmlNames.ATTR_comments)
        );
    }

    // Helper methods to extract attributes safely

    private String getStringAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        var variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof StringValue value) {
            return nullIfEmpty(value.val);
        }
        String asString = variable.getValue().toString();
        return nullIfEmpty(asString);
    }

    private Double getDoubleAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        var variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof DoubleValue value) {
            return value.val;
        }
        if (variable.getValue() instanceof IntegerValue value) {
            return (double) value.val;
        }
        if (variable.getValue() instanceof StringValue value) {
            String numeric = nullIfEmpty(value.val);
            if (numeric == null) {
                return null;
            }
            try {
                return Double.parseDouble(numeric);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean getBooleanAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        var variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof BooleanValue value) {
            return value.val;
        }
        if (variable.getValue() instanceof StringValue value) {
            String boolValue = nullIfEmpty(value.val);
            return boolValue != null ? Boolean.parseBoolean(boolValue) : null;
        }
        return null;
    }

    private Integer getIntegerAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        var variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof IntegerValue value) {
            return value.val;
        }
        if (variable.getValue() instanceof StringValue value) {
            String numeric = nullIfEmpty(value.val);
            if (numeric == null) {
                return null;
            }
            try {
                return Integer.parseInt(numeric);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private VariableMap getAttributes(Object obj) {
        if (obj instanceof AttributeContainer container) {
            return container.getAttributes();
        }
        return null;
    }

    private Integer getIndex(TextContent tc) {
        return getIntegerAttr(tc, DefaultXmlNames.ATTR_index);
    }

    private String getPageType(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_type);
    }

    private String getCustomAttribute(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_custom);
    }

    private Double getOrientation(Page page) {
        return getDoubleAttr(page, DefaultXmlNames.ATTR_orientation);
    }

    private String getPrimaryLanguage(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_primaryLanguage);
    }

    private String getSecondaryLanguage(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_secondaryLanguage);
    }

    private String getPrimaryScript(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_primaryScript);
    }

    private String getSecondaryScript(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_secondaryScript);
    }

    private String getReadingDirection(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_readingDirection);
    }

    private String getTextLineOrder(Page page) {
        return getStringAttr(page, DefaultXmlNames.ATTR_textLineOrder);
    }

    private Double getConfidence(Page page) {
        return getDoubleAttr(page, DefaultXmlNames.ATTR_conf);
    }

    private Double getRegionConfidence(Region region) {
        return getDoubleAttr(region, DefaultXmlNames.ATTR_conf);
    }

    private String getCustomFromRegion(Region region) {
        return getStringAttr(region, DefaultXmlNames.ATTR_custom);
    }

    private String getCommentsFromRegion(Region region) {
        return getStringAttr(region, DefaultXmlNames.ATTR_comments);
    }

    private boolean isAttributePresent(Object obj, String attrName) {
        if (presenceIndex == null || attrName == null) {
            return true;
        }
        if (obj instanceof Page) {
            return presenceIndex.hasPageAttribute(attrName);
        }
        String elementId = getElementId(obj);
        if (elementId != null) {
            return presenceIndex.hasAttributeForElementId(elementId, attrName);
        }
        return true;
    }

    private String getElementId(Object obj) {
        if (obj instanceof Region region && region.getId() != null) {
            return region.getId().toString();
        }
        if (obj instanceof LowLevelTextObject textObject && textObject.getId() != null) {
            return textObject.getId().toString();
        }
        if (obj instanceof Group group && group.getId() != null) {
            return group.getId().toString();
        }
        return null;
    }

    private boolean isMetadataFieldPresent(String field) {
        if (presenceIndex == null) {
            return true;
        }
        return switch (field) {
            case "creator" -> presenceIndex.hasMetadataCreator();
            case "created" -> presenceIndex.hasMetadataCreated();
            case "lastChange" -> presenceIndex.hasMetadataLastChange();
            case "comments" -> presenceIndex.hasMetadataComments();
            case "externalRef" -> presenceIndex.hasMetadataExternalRef();
            default -> false;
        };
    }

    // TODO: Implement bi-directional label mapping
    private List<String> mapLabelsToLabelIds(Page page) {
        // TODO: Implement mapping from page4j Labels to LabelSet IDs
        // page.getLabels() returns a Labels object
        // Need to map each Label to the corresponding LabelSet entry
        return null;
    }

    private List<String> mapRegionLabelsToLabelIds(Region region) {
        // TODO: Implement mapping from page4j Region Labels to LabelSet IDs
        return null;
    }
}
