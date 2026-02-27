package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.basic.ident.IdRegister;
import com.maxnth.page4j.basic.variable.StringValue;
import com.maxnth.page4j.basic.variable.VariableMap;
import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.layout.GeometricObjectImpl;
import com.maxnth.page4j.dla.page.layout.PageLayout;
import com.maxnth.page4j.dla.page.layout.logical.Group;
import com.maxnth.page4j.dla.page.layout.logical.GroupMember;
import com.maxnth.page4j.dla.page.layout.logical.ReadingOrder;
import com.maxnth.page4j.dla.page.layout.physical.Region;
import com.maxnth.page4j.dla.page.layout.physical.shared.RegionType;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextContainer;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextObject;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContent;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContentVariants;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Glyph;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextLine;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextRegion;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Word;
import com.maxnth.page4j.dla.page.metadata.MetaData;
import com.maxnth.page4j.maths.geometry.Polygon;

import de.uniwue.zpd.dachs.larex.backend.dto.page.*;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Set;

/**
 * Maps PageDto DTOs back to page4j Page objects.
 * This mapper creates page4j objects from JSON-deserialized DTOs,
 * enabling save/export operations.
 */
@Component
public class DtoToPage4jMapper {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // Image dimensions for coordinate conversion (set during toPage4j call)
    private int imageWidth;
    private int imageHeight;

    /**
     * Convert a PageDto to a page4j Page.
     */
    public Page toPage4j(PageDto dto) {
        if (dto == null) {
            return null;
        }

        // Store image dimensions for coordinate conversion
        this.imageWidth = dto.imageWidth();
        this.imageHeight = dto.imageHeight();

        // Create a new page with the latest schema
        Page page = new Page(PageXmlInputOutput.getLatestSchemaModel());

        // Set image filename
        page.setImageFilename(dto.imageFilename());

        // Set page dimensions
        PageLayout layout = page.getLayout();
        layout.setSize(dto.imageWidth(), dto.imageHeight());

        // Set metadata
        if (dto.metadata() != null) {
            setMetadata(page.getMetaData(), dto.metadata());
        }

        // Set pcGtsId if present
        if (dto.pcGtsId() != null) {
            try {
                page.setGtsId(dto.pcGtsId());
            } catch (IdRegister.InvalidIdException e) {
                // ID already registered or invalid
            }
        }

        setPageAttributes(page, dto);
        prunePageAttributes(page, dto);

        // Set border
        if (dto.border() != null) {
            layout.setBorder(new GeometricObjectImpl(toPolygon(dto.border())));
        }

        // Set print space
        if (dto.printSpace() != null) {
            layout.setPrintSpace(new GeometricObjectImpl(toPolygon(dto.printSpace())));
        }

        // Convert regions
        if (dto.regions() != null) {
            for (RegionDto regionDto : dto.regions()) {
                addRegion(layout, regionDto);
            }
        }

        // Convert reading order
        if (dto.readingOrder() != null) {
            setReadingOrder(layout, dto.readingOrder());
        }

        // TODO: Map LabelSet IDs back to page4j Labels
        mapLabelIdsToLabels(page, dto.labelIds());

        // page4j initializes many optional string attributes with empty values.
        // Remove them so they are omitted from XML output instead of failing schema enums.
        removeEmptyStringAttributes(page);

        return page;
    }

    private void setMetadata(MetaData metaData, MetadataDto dto) {
        metaData.setCreator(normalizeNullable(dto.creator()));
        if (hasText(dto.created())) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dto.created().trim(), ISO_FORMAT);
                metaData.setCreationTime(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (DateTimeParseException e) {
                // Ignore malformed dates
            }
        }
        if (hasText(dto.lastChange())) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dto.lastChange().trim(), ISO_FORMAT);
                metaData.setLastModifiedTime(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (DateTimeParseException e) {
                // Ignore malformed dates
            }
        }
        metaData.setComments(normalizeNullable(dto.comments()));
        metaData.setExternalRef(normalizeNullable(dto.externalRef()));
    }

    private void addRegion(PageLayout layout, RegionDto dto) {
        if (dto == null) {
            return;
        }

        RegionType regionType = toRegionType(dto.kind());
        Region region = layout.createRegion(regionType, dto.id());

        // Set coordinates
        if (dto.coords() != null) {
            region.setCoords(toPolygon(dto.coords()));
        }
        setRegionCommonAttributes(region, dto);

        // Handle TextRegion specifics
        if (region instanceof TextRegion textRegion) {
            setTextRegionAttributes(textRegion, dto);

            // Add text lines
            if (dto.textLines() != null) {
                for (TextLineDto lineDto : dto.textLines()) {
                    addTextLine(textRegion, lineDto);
                }
            }

            // Set text content variants
            if (dto.textContentVariants() != null) {
                setTextContentVariants(textRegion, dto.textContentVariants());
            }
        }

        pruneRegionAttributes(region, dto);

        // Handle nested regions
        if (dto.nestedRegions() != null) {
            for (RegionDto nestedDto : dto.nestedRegions()) {
                addNestedRegion(region, layout, nestedDto);
            }
        }

        // TODO: Map LabelSet IDs back to region Labels
        mapLabelIdsToRegionLabels(region, dto.labelIds());
    }

    private void addNestedRegion(Region parent, PageLayout layout, RegionDto dto) {
        RegionType regionType = toRegionType(dto.kind());
        Region nested = layout.createRegion(regionType, dto.id(), parent);

        if (dto.coords() != null) {
            nested.setCoords(toPolygon(dto.coords()));
        }
        setRegionCommonAttributes(nested, dto);

        if (nested instanceof TextRegion textRegion) {
            setTextRegionAttributes(textRegion, dto);
            if (dto.textLines() != null) {
                for (TextLineDto lineDto : dto.textLines()) {
                    addTextLine(textRegion, lineDto);
                }
            }
            if (dto.textContentVariants() != null) {
                setTextContentVariants(textRegion, dto.textContentVariants());
            }
        }

        pruneRegionAttributes(nested, dto);
    }

    private void setTextRegionAttributes(TextRegion textRegion, RegionDto dto) {
        setTextRegionTypeAttr(textRegion.getAttributes(), dto.type());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_orientation, dto.orientation());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_textColour, dto.textColour());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_bgColour, dto.bgColour());
        setBooleanAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_reverseVideo, dto.reverseVideo());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_fontSize, dto.fontSize());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_leading, dto.leading());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_kerning, dto.kerning());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_readingOrientation, dto.readingOrientation());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
        setBooleanAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_indented, dto.indented());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        setStringAttr(textRegion.getAttributes(), "production", dto.production());
    }

    private void addTextLine(TextRegion textRegion, TextLineDto dto) {
        if (dto == null) {
            return;
        }

        TextLine textLine = textRegion.createTextLine(dto.id());

        if (dto.coords() != null) {
            textLine.setCoords(toPolygon(dto.coords()));
        }
        if (dto.baseline() != null) {
            textLine.setBaseline(toPolygon(dto.baseline()));
        }

        // Set text content variants
        if (dto.textContentVariants() != null) {
            setTextContentVariants(textLine, dto.textContentVariants());
        }

        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_bold, dto.bold());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_italic, dto.italic());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_underlined, dto.underlined());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_subscript, dto.subscript());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_superscript, dto.superscript());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setStringAttr(textLine.getAttributes(), "production", dto.production());
        setDoubleAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        setIntegerAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_index, dto.index());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        pruneTextLineAttributes(textLine, dto);

        // Add words
        if (dto.words() != null) {
            for (WordDto wordDto : dto.words()) {
                addWord(textLine, wordDto);
            }
        }
    }

    private void addWord(TextLine textLine, WordDto dto) {
        if (dto == null) {
            return;
        }

        Word word = textLine.createWord(dto.id());

        if (dto.coords() != null) {
            word.setCoords(toPolygon(dto.coords()));
        }

        // Set text content variants
        if (dto.textContentVariants() != null) {
            setTextContentVariants(word, dto.textContentVariants());
        }

        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_bold, dto.bold());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_italic, dto.italic());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_underlined, dto.underlined());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_subscript, dto.subscript());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_superscript, dto.superscript());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_language, dto.language());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_script, dto.script());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setStringAttr(word.getAttributes(), "production", dto.production());
        setDoubleAttr(word.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        pruneWordAttributes(word, dto);

        // Add glyphs
        if (dto.glyphs() != null) {
            for (GlyphDto glyphDto : dto.glyphs()) {
                addGlyph(word, glyphDto);
            }
        }
    }

    private void addGlyph(Word word, GlyphDto dto) {
        if (dto == null) {
            return;
        }

        Glyph glyph = word.createGlyph(dto.id());

        if (dto.coords() != null) {
            glyph.setCoords(toPolygon(dto.coords()));
        }

        // Set text content variants
        if (dto.textContentVariants() != null) {
            setTextContentVariants(glyph, dto.textContentVariants());
        }

        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_bold, dto.bold());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_italic, dto.italic());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_underlined, dto.underlined());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_subscript, dto.subscript());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_superscript, dto.superscript());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_ligature, dto.ligature());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_symbol, dto.symbol());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_script, dto.script());
        setStringAttr(glyph.getAttributes(), "production", dto.production());
        setDoubleAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        pruneGlyphAttributes(glyph, dto);
    }

    private void setTextContentVariants(TextRegion textRegion, List<TextContentVariantDto> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        // First variant goes to the default slot
        TextContentVariantDto first = variants.get(0);
        if (first.unicode() != null && !first.unicode().isEmpty()) {
            textRegion.setText(first.unicode());
        }
        if (first.plainText() != null && !first.plainText().isEmpty()) {
            textRegion.setPlainText(first.plainText());
        }
        if (first.confidence() != null) {
            textRegion.setConfidence(first.confidence());
        }
        setTextContentIndex(textRegion.getTextContentVariant(0), first.index());

        // Additional variants
        for (int i = 1; i < variants.size(); i++) {
            TextContentVariantDto v = variants.get(i);
            var tc = textRegion.addTextContentVariant();
            if (v.unicode() != null && !v.unicode().isEmpty()) tc.setText(v.unicode());
            if (v.plainText() != null && !v.plainText().isEmpty()) tc.setPlainText(v.plainText());
            if (v.confidence() != null) tc.setConfidence(v.confidence());
            if (v.dataType() != null && !v.dataType().isEmpty()) tc.setDataType(v.dataType());
            if (v.dataTypeDetails() != null && !v.dataTypeDetails().isEmpty()) tc.setDataTypeDetails(v.dataTypeDetails());
            if (v.comments() != null && !v.comments().isEmpty()) tc.setComments(v.comments());
            setTextContentIndex(tc, v.index());
        }
    }

    private void setTextContentVariants(com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextObject textObj, List<TextContentVariantDto> variants) {
        if (variants == null || variants.isEmpty()) {
            return;
        }

        // First variant goes to the default slot
        TextContentVariantDto first = variants.get(0);
        if (first.unicode() != null && !first.unicode().isEmpty()) {
            textObj.setText(first.unicode());
        }
        if (first.plainText() != null && !first.plainText().isEmpty()) {
            textObj.setPlainText(first.plainText());
        }
        if (first.dataType() != null && !first.dataType().isEmpty()) {
            textObj.setDataType(first.dataType());
        }
        if (first.dataTypeDetails() != null && !first.dataTypeDetails().isEmpty()) {
            textObj.setDataTypeDetails(first.dataTypeDetails());
        }
        if (first.comments() != null && !first.comments().isEmpty()) {
            textObj.setComments(first.comments());
        }
        if (first.confidence() != null) {
            textObj.setConfidence(first.confidence());
        }
        setTextContentIndex(textObj.getTextContentVariant(0), first.index());

        // Additional variants
        for (int i = 1; i < variants.size(); i++) {
            TextContentVariantDto v = variants.get(i);
            var tc = textObj.addTextContentVariant();
            if (v.unicode() != null && !v.unicode().isEmpty()) tc.setText(v.unicode());
            if (v.plainText() != null && !v.plainText().isEmpty()) tc.setPlainText(v.plainText());
            if (v.confidence() != null) tc.setConfidence(v.confidence());
            if (v.dataType() != null && !v.dataType().isEmpty()) tc.setDataType(v.dataType());
            if (v.dataTypeDetails() != null && !v.dataTypeDetails().isEmpty()) tc.setDataTypeDetails(v.dataTypeDetails());
            if (v.comments() != null && !v.comments().isEmpty()) tc.setComments(v.comments());
            setTextContentIndex(tc, v.index());
        }
    }

    private void setTextContentIndex(com.maxnth.page4j.dla.page.layout.physical.text.TextContent tc, Integer index) {
        if (tc == null || tc.getAttributes() == null) return;
        var indexVar = findVariable(tc.getAttributes(), com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames.ATTR_index);
        if (index == null) {
            removeAttribute(tc.getAttributes(), com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames.ATTR_index);
            return;
        }
        if (indexVar == null) return;
        try {
            indexVar.setValue(com.maxnth.page4j.basic.variable.VariableValue.of(index));
        } catch (com.maxnth.page4j.basic.variable.Variable.WrongVariableTypeException e) {
            // ignore
        }
    }

    private void setReadingOrder(PageLayout layout, ReadingOrderDto dto) {
        if (dto == null || dto.root() == null) {
            return;
        }

        ReadingOrder readingOrder = layout.createReadingOrder();
        if (dto.confidence() != null) {
            readingOrder.setConfidence(dto.confidence());
        }

        // Populate the root group
        populateGroup(readingOrder.getRoot(), dto.root(), layout);
    }

    private void populateGroup(Group group, ReadingOrderDto.GroupDto dto, PageLayout layout) {
        if (dto == null) {
            return;
        }

        group.setOrdered(dto.ordered());
        if (dto.caption() != null) {
            group.setCaption(dto.caption());
        }
        if (dto.regionRef() != null) {
            group.setRegionRef(dto.regionRef());
        }
        setStringAttr(group.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(group.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());

        // Add members
        if (dto.members() != null) {
            for (ReadingOrderDto.GroupMemberDto member : dto.members()) {
                if (member instanceof ReadingOrderDto.RegionRefDto refDto) {
                    try {
                        group.addRegionRef(refDto.regionRef());
                    } catch (Exception e) {
                        // Region ref creation failed
                    }
                } else if (member instanceof ReadingOrderDto.NestedGroupDto nestedDto) {
                    try {
                        Group childGroup = group.createChildGroup();
                        populateGroup(childGroup, nestedDto.group(), layout);
                    } catch (Exception e) {
                        // Child group creation failed
                    }
                }
            }
        }
    }

    private Polygon toPolygon(PolygonDto dto) {
        if (dto == null || dto.points() == null || dto.points().isEmpty()) {
            return null;
        }

        Polygon polygon = new Polygon();
        for (PointDto point : dto.points()) {
            // Convert world coordinates back to pixel coordinates for PAGE XML
            int pixelX = CoordinateUtils.worldToPixelX(point.x(), imageWidth);
            int pixelY = CoordinateUtils.worldToPixelY(point.y(), imageHeight);
            polygon.addPoint(pixelX, pixelY);
        }
        if (dto.confidence() != null) {
            polygon.setConfidence(dto.confidence());
        }
        return polygon;
    }

    private RegionType toRegionType(RegionKind kind) {
        if (kind == null) {
            return RegionType.UnknownRegion;
        }

        return switch (kind) {
            case TextRegion -> RegionType.TextRegion;
            case ImageRegion -> RegionType.ImageRegion;
            case LineDrawingRegion -> RegionType.LineDrawingRegion;
            case GraphicRegion -> RegionType.GraphicRegion;
            case TableRegion -> RegionType.TableRegion;
            case ChartRegion -> RegionType.ChartRegion;
            case MapRegion -> RegionType.MapRegion;
            case SeparatorRegion -> RegionType.SeparatorRegion;
            case MathsRegion -> RegionType.MathsRegion;
            case ChemRegion -> RegionType.ChemRegion;
            case MusicRegion -> RegionType.MusicRegion;
            case AdvertRegion -> RegionType.AdvertRegion;
            case NoiseRegion -> RegionType.NoiseRegion;
            case UnknownRegion -> RegionType.UnknownRegion;
            case CustomRegion -> RegionType.CustomRegion;
        };
    }

    // TODO: Implement bi-directional label mapping
    private void mapLabelIdsToLabels(Page page, List<String> labelIds) {
        // TODO: Look up LabelSet entries by ID and create corresponding page4j Labels
        // This requires access to the LabelSetService to resolve label definitions
    }

    private void mapLabelIdsToRegionLabels(Region region, List<String> labelIds) {
        // TODO: Look up LabelSet entries by ID and create corresponding page4j Labels for this region
    }

    private void setPageAttributes(Page page, PageDto dto) {
        setIntegerAttr(page.getAttributes(), DefaultXmlNames.ATTR_imageXResolution, dto.imageXResolution());
        setIntegerAttr(page.getAttributes(), "imageYResolution", dto.imageYResolution());
        setStringAttr(page.getAttributes(), "imageResolutionUnit", dto.imageResolutionUnit());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_type, dto.type());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setDoubleAttr(page.getAttributes(), DefaultXmlNames.ATTR_orientation, dto.orientation());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setStringAttr(page.getAttributes(), DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
        setDoubleAttr(page.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
    }

    private void prunePageAttributes(Page page, PageDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_imageXResolution, dto.imageXResolution());
        addAllowedIfPresent(allowed, "imageYResolution", dto.imageYResolution());
        addAllowedIfPresent(allowed, "imageResolutionUnit", dto.imageResolutionUnit());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_type, dto.type());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_orientation, dto.orientation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        pruneAttributes(page.getAttributes(), allowed);
    }

    private void setRegionCommonAttributes(Region region, RegionDto dto) {
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        setBooleanAttr(region.getAttributes(), DefaultXmlNames.ATTR_continuation, dto.continuation());
        setDoubleAttr(region.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        if (!(region instanceof TextRegion)) {
            setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_type, dto.type());
            setDoubleAttr(region.getAttributes(), DefaultXmlNames.ATTR_orientation, dto.orientation());
        }
    }

    private void pruneRegionAttributes(Region region, RegionDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_continuation, dto.continuation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        if (region instanceof TextRegion) {
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_type, dto.type());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_orientation, dto.orientation());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textColour, dto.textColour());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bgColour, dto.bgColour());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_reverseVideo, dto.reverseVideo());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_fontSize, dto.fontSize());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_leading, dto.leading());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_kerning, dto.kerning());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingOrientation, dto.readingOrientation());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_indented, dto.indented());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
            addAllowedIfPresent(allowed, "production", dto.production());
        } else {
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_type, dto.type());
            addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_orientation, dto.orientation());
        }
        pruneAttributes(region.getAttributes(), allowed);
    }

    private void pruneTextLineAttributes(TextLine textLine, TextLineDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, dto.bold());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, dto.italic());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, dto.underlined());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, dto.subscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, dto.superscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_index, dto.index());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        pruneAttributes(textLine.getAttributes(), allowed);
    }

    private void pruneWordAttributes(Word word, WordDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, dto.bold());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, dto.italic());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, dto.underlined());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, dto.subscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, dto.superscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_language, dto.language());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_script, dto.script());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        pruneAttributes(word.getAttributes(), allowed);
    }

    private void pruneGlyphAttributes(Glyph glyph, GlyphDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, dto.bold());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, dto.italic());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, dto.underlined());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, dto.underlineStyle());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, dto.subscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, dto.superscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, dto.strikethrough());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, dto.smallCaps());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, dto.letterSpaced());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_ligature, dto.ligature());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_symbol, dto.symbol());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_script, dto.script());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        pruneAttributes(glyph.getAttributes(), allowed);
    }

    private void addAllowedIfPresent(Set<String> allowed, String attrName, Object value) {
        if (allowed == null || attrName == null || value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        allowed.add(attrName);
    }

    private void pruneAttributes(VariableMap attributes, Set<String> allowed) {
        if (attributes == null) {
            return;
        }
        for (int i = attributes.getSize() - 1; i >= 0; i--) {
            var variable = attributes.get(i);
            if (variable == null || variable.getName() == null) {
                attributes.remove(i);
                continue;
            }
            if (allowed == null || !allowed.contains(variable.getName())) {
                attributes.remove(i);
            }
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void setStringAttr(VariableMap attributes, String attrName, String value) {
        if (attributes == null) {
            return;
        }
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            removeAttribute(attributes, attrName);
            return;
        }
        var variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        String coerced = coerceStringValue(attrName, normalized);
        variable.parseValue(coerced);
    }

    private void setTextRegionTypeAttr(VariableMap attributes, String value) {
        if (attributes == null) {
            return;
        }

        String normalized = normalizeNullable(value);
        // LAREX stores custom TextRegion subtypes in @custom structure.type and exports PAGE-valid type="other".
        if (normalized != null && "custom".equalsIgnoreCase(normalized)) {
            normalized = "other";
        }
        if (normalized == null) {
            removeAttribute(attributes, DefaultXmlNames.ATTR_type);
            return;
        }

        var variable = findVariable(attributes, DefaultXmlNames.ATTR_type);
        if (variable == null) {
            return;
        }
        variable.parseValue(normalized);
    }

    private void setBooleanAttr(VariableMap attributes, String attrName, Boolean value) {
        if (attributes == null) {
            return;
        }
        if (value == null) {
            removeAttribute(attributes, attrName);
            return;
        }
        var variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        variable.parseValue(Boolean.toString(value));
    }

    private void setIntegerAttr(VariableMap attributes, String attrName, Integer value) {
        if (attributes == null) {
            return;
        }
        if (value == null) {
            removeAttribute(attributes, attrName);
            return;
        }
        var variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        variable.parseValue(Integer.toString(value));
    }

    private void setDoubleAttr(VariableMap attributes, String attrName, Double value) {
        if (attributes == null) {
            return;
        }
        if (value == null) {
            removeAttribute(attributes, attrName);
            return;
        }
        var variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        variable.parseValue(Double.toString(value));
    }

    private com.maxnth.page4j.basic.variable.Variable findVariable(VariableMap attributes, String attrName) {
        if (attributes == null || attrName == null) {
            return null;
        }
        var byName = attributes.get(attrName);
        if (byName != null) {
            return byName;
        }
        for (int i = 0; i < attributes.getSize(); i++) {
            var variable = attributes.get(i);
            if (variable != null && attrName.equals(variable.getName())) {
                return variable;
            }
        }
        return null;
    }

    private String coerceStringValue(String attrName, String value) {
        if (value == null) {
            return null;
        }

        if (DefaultXmlNames.ATTR_primaryLanguage.equals(attrName)
            || DefaultXmlNames.ATTR_secondaryLanguage.equals(attrName)
            || DefaultXmlNames.ATTR_language.equals(attrName)) {
            return coerceLanguageValue(value);
        }

        return value;
    }

    private String coerceLanguageValue(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String normalized = trimmed.replace('_', '-');
        String lower = normalized.toLowerCase(Locale.ROOT);

        // PAGE LanguageSimpleType in page4j expects language names (e.g. "English"),
        // while UI users often enter ISO codes (e.g. "en", "deu"). Convert both.
        if (normalized.matches("^[A-Za-z]{2,3}([_-][A-Za-z]{2})?$")) {
            Locale locale = Locale.forLanguageTag(lower);
            String display = locale.getDisplayLanguage(Locale.ENGLISH);
            if (display != null && !display.isBlank() && !"und".equalsIgnoreCase(display)) {
                return display;
            }
        }

        for (String iso2 : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(iso2);
            String display = locale.getDisplayLanguage(Locale.ENGLISH);
            if (display == null || display.isBlank()) {
                continue;
            }

            if (display.equalsIgnoreCase(trimmed)) {
                return display;
            }

            try {
                String iso3 = locale.getISO3Language();
                if (iso3 != null && iso3.equalsIgnoreCase(trimmed)) {
                    return display;
                }
            } catch (Exception ignored) {
                // Ignore locales without ISO3 mapping
            }
        }

        return trimmed;
    }

    private void removeAttribute(VariableMap attributes, String attrName) {
        if (attributes == null || attrName == null) {
            return;
        }
        for (int i = attributes.getSize() - 1; i >= 0; i--) {
            var variable = attributes.get(i);
            if (variable != null && attrName.equals(variable.getName())) {
                attributes.remove(i);
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void removeEmptyStringAttributes(Page page) {
        if (page == null) {
            return;
        }

        removeEmptyStringAttributes(page.getAttributes());

        var layout = page.getLayout();
        if (layout != null) {
            for (int i = 0; i < layout.getRegionCount(); i++) {
                sanitizeRegion(layout.getRegion(i));
            }

            var readingOrder = layout.getReadingOrder();
            if (readingOrder != null && readingOrder.getRoot() != null) {
                sanitizeGroup(readingOrder.getRoot());
            }
        }
    }

    private void sanitizeGroup(Group group) {
        if (group == null) {
            return;
        }

        removeEmptyStringAttributes(group.getAttributes());
        for (int i = 0; i < group.getSize(); i++) {
            GroupMember member = group.getMember(i);
            if (member instanceof Group child) {
                sanitizeGroup(child);
            }
        }
    }

    private void sanitizeRegion(Region region) {
        if (region == null) {
            return;
        }

        removeEmptyStringAttributes(region.getAttributes());
        if (region instanceof TextContentVariants variants) {
            sanitizeTextContentVariants(variants);
        }

        if (region instanceof LowLevelTextContainer container && container.hasTextObjects()) {
            for (int i = 0; i < container.getTextObjectCount(); i++) {
                sanitizeTextObject(container.getTextObject(i));
            }
        }

        for (int i = 0; i < region.getRegionCount(); i++) {
            sanitizeRegion(region.getRegion(i));
        }
    }

    private void sanitizeTextObject(LowLevelTextObject textObject) {
        if (textObject == null) {
            return;
        }

        removeEmptyStringAttributes(textObject.getAttributes());
        if (textObject instanceof TextContentVariants variants) {
            sanitizeTextContentVariants(variants);
        }

        if (textObject instanceof LowLevelTextContainer container && container.hasTextObjects()) {
            for (int i = 0; i < container.getTextObjectCount(); i++) {
                sanitizeTextObject(container.getTextObject(i));
            }
        }
    }

    private void sanitizeTextContentVariants(TextContentVariants variants) {
        if (variants == null) {
            return;
        }

        for (int i = 0; i < variants.getTextContentVariantCount(); i++) {
            TextContent textContent = variants.getTextContentVariant(i);
            if (textContent != null) {
                removeEmptyStringAttributes(textContent.getAttributes());
            }
        }
    }

    private void removeEmptyStringAttributes(VariableMap attributes) {
        if (attributes == null) {
            return;
        }

        for (int i = attributes.getSize() - 1; i >= 0; i--) {
            var variable = attributes.get(i);
            if (variable == null || variable.getValue() == null) {
                continue;
            }

            if (variable.getValue() instanceof StringValue stringValue) {
                if (stringValue.val == null || stringValue.val.isBlank()) {
                    attributes.remove(i);
                }
            }
        }
    }
}
