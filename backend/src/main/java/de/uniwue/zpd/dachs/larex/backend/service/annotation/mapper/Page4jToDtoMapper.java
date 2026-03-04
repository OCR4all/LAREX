package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.basic.labels.HasLabels;
import com.maxnth.page4j.basic.labels.Label;
import com.maxnth.page4j.basic.labels.LabelGroup;
import com.maxnth.page4j.basic.labels.Labels;
import com.maxnth.page4j.basic.variable.BooleanValue;
import com.maxnth.page4j.basic.variable.DoubleValue;
import com.maxnth.page4j.basic.variable.DoubleVariable;
import com.maxnth.page4j.basic.variable.IntegerValue;
import com.maxnth.page4j.basic.variable.IntegerVariable;
import com.maxnth.page4j.basic.variable.StringValue;
import com.maxnth.page4j.basic.variable.StringVariable;
import com.maxnth.page4j.basic.variable.Variable;
import com.maxnth.page4j.basic.variable.VariableMap;
import com.maxnth.page4j.dla.page.AlternativeImage;
import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames;
import com.maxnth.page4j.dla.page.layout.PageLayout;
import com.maxnth.page4j.dla.page.layout.logical.ContentObjectRelation;
import com.maxnth.page4j.dla.page.layout.logical.Group;
import com.maxnth.page4j.dla.page.layout.logical.GroupMember;
import com.maxnth.page4j.dla.page.layout.logical.Layer;
import com.maxnth.page4j.dla.page.layout.logical.Layers;
import com.maxnth.page4j.dla.page.layout.logical.ReadingOrder;
import com.maxnth.page4j.dla.page.layout.logical.RegionRef;
import com.maxnth.page4j.dla.page.layout.logical.Relations;
import com.maxnth.page4j.dla.page.layout.physical.AttributeContainer;
import com.maxnth.page4j.dla.page.layout.physical.Region;
import com.maxnth.page4j.dla.page.layout.physical.impl.TableGrid;
import com.maxnth.page4j.dla.page.layout.physical.impl.TableRegion;
import com.maxnth.page4j.dla.page.layout.physical.role.RegionRole;
import com.maxnth.page4j.dla.page.layout.physical.shared.RoleType;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextContainer;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextObject;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContent;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContentVariants;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.GraphemeElement;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.GraphemeGroup;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.NonPrintingCharacter;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Glyph;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextLine;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextRegion;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Word;
import com.maxnth.page4j.dla.page.metadata.MetaData;
import com.maxnth.page4j.dla.page.metadata.MetadataItem;
import com.maxnth.page4j.maths.geometry.Point;
import com.maxnth.page4j.maths.geometry.Polygon;
import de.uniwue.zpd.dachs.larex.backend.dto.page.AlternativeImageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.GlyphDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.GraphemeElementDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.GraphemesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.GridDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.GridPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.LayerDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.LayersDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.MetadataDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.MetadataItemDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.RelationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.RelationsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.RolesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.TableCellRoleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.TextStyleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.UserAttributeDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.UserDefinedDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.WordDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.parser.PageXmlPresenceIndex;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Page4jToDtoMapper {

    private int imageWidth;
    private int imageHeight;
    private PageXmlPresenceIndex presenceIndex;

    private static String nullIfEmpty(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    public PageDto toDto(Page page) {
        return toDto(page, null);
    }

    public PageDto toDto(Page page, PageXmlPresenceIndex presenceIndex) {
        if (page == null) {
            return null;
        }

        this.presenceIndex = presenceIndex;
        PageLayout layout = page.getLayout();
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
            getStringAttr(page, DefaultXmlNames.ATTR_type),
            getStringAttr(page, DefaultXmlNames.ATTR_custom),
            getDoubleAttr(page, DefaultXmlNames.ATTR_orientation),
            getStringAttr(page, DefaultXmlNames.ATTR_primaryLanguage),
            getStringAttr(page, DefaultXmlNames.ATTR_secondaryLanguage),
            getStringAttr(page, DefaultXmlNames.ATTR_primaryScript),
            getStringAttr(page, DefaultXmlNames.ATTR_secondaryScript),
            getStringAttr(page, DefaultXmlNames.ATTR_readingDirection),
            getStringAttr(page, DefaultXmlNames.ATTR_textLineOrder),
            getDoubleAttr(page, DefaultXmlNames.ATTR_conf),
            toBorderPolygonDto(layout),
            toPrintSpacePolygonDto(layout),
            toRegionDtos(layout),
            toReadingOrderDto(layout.getReadingOrder()),
            toAlternativeImageDtos(page.getAlternativeImages()),
            toLabelsDtos(page.getLabels()),
            toUserDefinedDto(page.getUserDefinedAttributes(false)),
            toTextStyleDto(page),
            toLayersDto(layout.getLayers()),
            toRelationsDto(layout.getRelations()),
            page.getFormatVersion() != null ? page.getFormatVersion().toString() : null,
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
        UserDefinedDto userDefined = toUserDefinedDto(metaData.getUserDefinedAttributes(false));
        List<MetadataItemDto> items = toMetadataItemDtos(metaData.getMetadataItems());
        if (creator == null
            && created == null
            && lastChange == null
            && comments == null
            && externalRef == null
            && userDefined == null
            && (items == null || items.isEmpty())) {
            return null;
        }
        return new MetadataDto(creator, created, lastChange, comments, externalRef, userDefined, items);
    }

    private List<MetadataItemDto> toMetadataItemDtos(List<MetadataItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        List<MetadataItemDto> dtos = new ArrayList<>();
        for (MetadataItem item : items) {
            if (item == null) {
                continue;
            }
            dtos.add(new MetadataItemDto(
                getStringAttr(item, DefaultXmlNames.ATTR_type),
                getStringAttr(item, DefaultXmlNames.ATTR_name),
                getStringAttr(item, DefaultXmlNames.ATTR_value),
                getStringAttr(item, DefaultXmlNames.ATTR_date),
                toLabelsDtos(item.getLabels())
            ));
        }
        return dtos.isEmpty() ? null : dtos;
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
            regions.add(toRegionDto(layout.getRegion(i)));
        }
        return regions;
    }

    private RegionDto toRegionDto(Region region) {
        if (region == null) {
            return null;
        }

        List<TextLineDto> textLines = null;
        List<TextContentVariantDto> textContentVariants = null;
        if (region instanceof TextRegion textRegion) {
            textLines = toTextLineDtos(textRegion);
            textContentVariants = toTextContentVariantDtos(textRegion);
        }

        List<RegionDto> nestedRegions = null;
        if (region.getRegionCount() > 0) {
            nestedRegions = new ArrayList<>();
            for (int i = 0; i < region.getRegionCount(); i++) {
                nestedRegions.add(toRegionDto(region.getRegion(i)));
            }
        }

        return new RegionDto(
            region.getId() != null ? region.getId().toString() : null,
            RegionKind.fromPage4jName(region.getType().getName()),
            toPolygonDto(region.getCoords()),
            textLines,
            textContentVariants,
            toAlternativeImageDtos(region.getAlternativeImages()),
            toLabelsDtos(region.getLabels()),
            toUserDefinedDto(region.getUserDefinedAttributes(false)),
            toRolesDto(region),
            toGridDto(region),
            toTextStyleDto(region),
            getStringAttr(region, DefaultXmlNames.ATTR_type),
            getDoubleAttr(region, DefaultXmlNames.ATTR_orientation),
            getStringAttr(region, DefaultXmlNames.ATTR_textColour),
            getStringAttr(region, DefaultXmlNames.ATTR_bgColour),
            getBooleanAttr(region, DefaultXmlNames.ATTR_reverseVideo),
            getDoubleAttr(region, DefaultXmlNames.ATTR_fontSize),
            getStringAttr(region, "fontFamily"),
            getBooleanAttr(region, "serif"),
            getBooleanAttr(region, "monospace"),
            getIntegerAttr(region, DefaultXmlNames.ATTR_xHeight),
            getIntegerAttr(region, DefaultXmlNames.ATTR_leading),
            getIntegerAttr(region, DefaultXmlNames.ATTR_kerning),
            getStringAttr(region, "align"),
            getIntegerAttr(region, DefaultXmlNames.ATTR_textColourRgb),
            getIntegerAttr(region, "bgColourRgb"),
            getStringAttr(region, DefaultXmlNames.ATTR_readingDirection),
            getDoubleAttr(region, DefaultXmlNames.ATTR_readingOrientation),
            getStringAttr(region, DefaultXmlNames.ATTR_textLineOrder),
            getBooleanAttr(region, DefaultXmlNames.ATTR_indented),
            getStringAttr(region, DefaultXmlNames.ATTR_primaryLanguage),
            getStringAttr(region, DefaultXmlNames.ATTR_secondaryLanguage),
            getStringAttr(region, DefaultXmlNames.ATTR_primaryScript),
            getStringAttr(region, DefaultXmlNames.ATTR_secondaryScript),
            getStringAttr(region, "production"),
            getIntegerAttr(region, DefaultXmlNames.ATTR_numColours),
            getBooleanAttr(region, DefaultXmlNames.ATTR_embText),
            getStringAttr(region, DefaultXmlNames.ATTR_colourDepth),
            getStringAttr(region, DefaultXmlNames.ATTR_lineColour),
            getBooleanAttr(region, DefaultXmlNames.ATTR_lineSeparators),
            getIntegerAttr(region, DefaultXmlNames.ATTR_rows),
            getIntegerAttr(region, DefaultXmlNames.ATTR_columns),
            getStringAttr(region, DefaultXmlNames.ATTR_colour),
            getStringAttr(region, DefaultXmlNames.ATTR_penColour),
            getBooleanAttr(region, DefaultXmlNames.ATTR_borderPresent),
            nestedRegions,
            getDoubleAttr(region, DefaultXmlNames.ATTR_conf),
            getStringAttr(region, DefaultXmlNames.ATTR_custom),
            getStringAttr(region, DefaultXmlNames.ATTR_comments),
            getBooleanAttr(region, DefaultXmlNames.ATTR_continuation),
            mapRegionLabelsToLabelIds(region)
        );
    }

    private RolesDto toRolesDto(Region region) {
        if (region == null || !region.hasRole(RoleType.TableCellRole)) {
            return null;
        }
        RegionRole role = region.getRole(RoleType.TableCellRole);
        if (role == null) {
            return null;
        }
        return new RolesDto(new TableCellRoleDto(
            getIntegerAttr(role, DefaultXmlNames.ATTR_rowIndex),
            getIntegerAttr(role, DefaultXmlNames.ATTR_columnIndex),
            getIntegerAttr(role, DefaultXmlNames.ATTR_rowSpan),
            getIntegerAttr(role, DefaultXmlNames.ATTR_colSpan),
            getBooleanAttr(role, DefaultXmlNames.ATTR_header)
        ));
    }

    private GridDto toGridDto(Region region) {
        if (!(region instanceof TableRegion tableRegion) || tableRegion.getGrid() == null) {
            return null;
        }
        TableGrid grid = tableRegion.getGrid();
        List<GridPointsDto> rows = new ArrayList<>();
        for (int i = 0; i < grid.getRows().size(); i++) {
            TableGrid.TableGridRow row = grid.getRows().get(i);
            rows.add(new GridPointsDto(i, toPolygonDto(row.getCoords())));
        }
        return rows.isEmpty() ? null : new GridDto(rows);
    }

    private List<TextLineDto> toTextLineDtos(TextRegion textRegion) {
        if (!textRegion.hasTextObjects()) {
            return null;
        }
        List<TextLineDto> lines = new ArrayList<>();
        for (int i = 0; i < textRegion.getTextObjectCount(); i++) {
            if (textRegion.getTextObject(i) instanceof TextLine textLine) {
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
            toAlternativeImageDtos(textLine.getAlternativeImages()),
            toLabelsDtos(textLine.getLabels()),
            toUserDefinedDto(textLine.getUserDefinedAttributes(false)),
            toTextStyleDto(textLine),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_bold),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_italic),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_underlined),
            getStringAttr(textLine, DefaultXmlNames.ATTR_underlineStyle),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_subscript),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_superscript),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_strikethrough),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_smallCaps),
            getBooleanAttr(textLine, DefaultXmlNames.ATTR_letterSpaced),
            getStringAttr(textLine, DefaultXmlNames.ATTR_primaryLanguage),
            getStringAttr(textLine, DefaultXmlNames.ATTR_primaryScript),
            getStringAttr(textLine, DefaultXmlNames.ATTR_secondaryScript),
            getStringAttr(textLine, DefaultXmlNames.ATTR_readingDirection),
            getStringAttr(textLine, "production"),
            getDoubleAttr(textLine, DefaultXmlNames.ATTR_conf),
            getIntegerAttr(textLine, DefaultXmlNames.ATTR_index),
            getStringAttr(textLine, DefaultXmlNames.ATTR_custom),
            getStringAttr(textLine, DefaultXmlNames.ATTR_comments)
        );
    }

    private List<WordDto> toWordDtos(TextLine textLine) {
        if (!textLine.hasTextObjects()) {
            return null;
        }
        List<WordDto> words = new ArrayList<>();
        for (int i = 0; i < textLine.getTextObjectCount(); i++) {
            if (textLine.getTextObject(i) instanceof Word word) {
                words.add(toWordDto(word));
            }
        }
        return words.isEmpty() ? null : words;
    }

    private WordDto toWordDto(Word word) {
        String script = getStringAttr(word, DefaultXmlNames.ATTR_script);
        String primaryScript = getStringAttr(word, DefaultXmlNames.ATTR_primaryScript);
        String secondaryScript = getStringAttr(word, DefaultXmlNames.ATTR_secondaryScript);
        return new WordDto(
            word.getId() != null ? word.getId().toString() : null,
            toPolygonDto(word.getCoords()),
            toTextContentVariantDtos(word),
            toGlyphDtos(word),
            toAlternativeImageDtos(word.getAlternativeImages()),
            toLabelsDtos(word.getLabels()),
            toUserDefinedDto(word.getUserDefinedAttributes(false)),
            toTextStyleDto(word),
            getBooleanAttr(word, DefaultXmlNames.ATTR_bold),
            getBooleanAttr(word, DefaultXmlNames.ATTR_italic),
            getBooleanAttr(word, DefaultXmlNames.ATTR_underlined),
            getStringAttr(word, DefaultXmlNames.ATTR_underlineStyle),
            getBooleanAttr(word, DefaultXmlNames.ATTR_subscript),
            getBooleanAttr(word, DefaultXmlNames.ATTR_superscript),
            getBooleanAttr(word, DefaultXmlNames.ATTR_strikethrough),
            getBooleanAttr(word, DefaultXmlNames.ATTR_smallCaps),
            getBooleanAttr(word, DefaultXmlNames.ATTR_letterSpaced),
            getStringAttr(word, DefaultXmlNames.ATTR_language),
            primaryScript != null ? primaryScript : script,
            secondaryScript,
            script,
            getStringAttr(word, DefaultXmlNames.ATTR_readingDirection),
            getStringAttr(word, "production"),
            getDoubleAttr(word, DefaultXmlNames.ATTR_conf),
            getStringAttr(word, DefaultXmlNames.ATTR_custom),
            getStringAttr(word, DefaultXmlNames.ATTR_comments)
        );
    }

    private List<GlyphDto> toGlyphDtos(Word word) {
        if (!word.hasTextObjects()) {
            return null;
        }
        List<GlyphDto> glyphs = new ArrayList<>();
        for (int i = 0; i < word.getTextObjectCount(); i++) {
            if (word.getTextObject(i) instanceof Glyph glyph) {
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
            toAlternativeImageDtos(glyph.getAlternativeImages()),
            toLabelsDtos(glyph.getLabels()),
            toUserDefinedDto(glyph.getUserDefinedAttributes(false)),
            toTextStyleDto(glyph),
            toGraphemesDto(glyph),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_bold),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_italic),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_underlined),
            getStringAttr(glyph, DefaultXmlNames.ATTR_underlineStyle),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_subscript),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_superscript),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_strikethrough),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_smallCaps),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_letterSpaced),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_ligature),
            getBooleanAttr(glyph, DefaultXmlNames.ATTR_symbol),
            getStringAttr(glyph, DefaultXmlNames.ATTR_script),
            getStringAttr(glyph, "production"),
            getDoubleAttr(glyph, DefaultXmlNames.ATTR_conf),
            getStringAttr(glyph, DefaultXmlNames.ATTR_custom),
            getStringAttr(glyph, DefaultXmlNames.ATTR_comments)
        );
    }

    private GraphemesDto toGraphemesDto(Glyph glyph) {
        if (glyph == null || !glyph.hasGraphemes()) {
            return null;
        }
        List<GraphemeElementDto> elements = new ArrayList<>();
        for (GraphemeElement element : glyph.getGraphemes()) {
            GraphemeElementDto dto = toGraphemeElementDto(element);
            if (dto != null) {
                elements.add(dto);
            }
        }
        return elements.isEmpty() ? null : new GraphemesDto(elements);
    }

    private GraphemeElementDto toGraphemeElementDto(GraphemeElement element) {
        if (element == null) {
            return null;
        }

        String kind = "grapheme";
        PolygonDto coords = null;
        List<GraphemeElementDto> members = null;
        List<LabelsDto> labels = null;

        if (element instanceof com.maxnth.page4j.dla.page.layout.physical.text.graphemes.Grapheme grapheme) {
            coords = toPolygonDto(grapheme.getCoords());
            labels = toLabelsDtos(grapheme.getLabels());
            kind = "grapheme";
        } else if (element instanceof NonPrintingCharacter) {
            kind = "nonPrintingChar";
        } else if (element instanceof GraphemeGroup group) {
            kind = "graphemeGroup";
            if (group.getSize() > 0) {
                members = new ArrayList<>();
                for (GraphemeElement member : group.getGraphemes()) {
                    GraphemeElementDto memberDto = toGraphemeElementDto(member);
                    if (memberDto != null) {
                        members.add(memberDto);
                    }
                }
            }
        }

        return new GraphemeElementDto(
            element.getType() != null ? kind : null,
            element.getId() != null ? element.getId().toString() : null,
            getIntegerAttr(element, DefaultXmlNames.ATTR_index),
            getStringAttr(element, DefaultXmlNames.ATTR_charType),
            getBooleanAttr(element, DefaultXmlNames.ATTR_ligature),
            getStringAttr(element, DefaultXmlNames.ATTR_custom),
            getStringAttr(element, DefaultXmlNames.ATTR_comments),
            coords,
            toTextContentVariantDtos(element),
            labels,
            members
        );
    }

    private List<TextContentVariantDto> toTextContentVariantDtos(TextContentVariants textObject) {
        if (textObject == null || textObject.getTextContentVariantCount() == 0) {
            return null;
        }
        String ownerId = null;
        if (textObject instanceof Region region && region.getId() != null) {
            ownerId = region.getId().toString();
        } else if (textObject instanceof LowLevelTextObject lowLevelTextObject && lowLevelTextObject.getId() != null) {
            ownerId = lowLevelTextObject.getId().toString();
        } else if (textObject instanceof GraphemeElement graphemeElement && graphemeElement.getId() != null) {
            ownerId = graphemeElement.getId().toString();
        }

        List<TextContentVariantDto> variants = new ArrayList<>();
        for (int i = 0; i < textObject.getTextContentVariantCount(); i++) {
            TextContent tc = textObject.getTextContentVariant(i);
            variants.add(new TextContentVariantDto(
                nullIfEmpty(tc.getText()),
                nullIfEmpty(tc.getPlainText()),
                getTextEquivConfidence(tc, ownerId, i),
                getIntegerAttr(tc, DefaultXmlNames.ATTR_index),
                nullIfEmpty(tc.getDataType()),
                nullIfEmpty(tc.getDataTypeDetails()),
                nullIfEmpty(tc.getComments())
            ));
        }
        return variants;
    }

    private Double getTextEquivConfidence(TextContent textContent, String ownerId, int textEquivPosition) {
        if (presenceIndex != null && ownerId != null && !presenceIndex.hasTextEquivConfidenceForElementId(ownerId, textEquivPosition)) {
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
            Point point = polygon.getPoint(i);
            points.add(new PointDto(
                CoordinateUtils.pixelToWorldX(point.x, imageWidth),
                CoordinateUtils.pixelToWorldY(point.y, imageHeight)
            ));
        }
        return new PolygonDto(points, polygon.getConfidence());
    }

    private ReadingOrderDto toReadingOrderDto(ReadingOrder readingOrder) {
        if (readingOrder == null || readingOrder.getRoot() == null) {
            return null;
        }
        return new ReadingOrderDto(toGroupDto(readingOrder.getRoot(), null), readingOrder.getConfidence());
    }

    private ReadingOrderDto.GroupDto toGroupDto(Group group, Integer index) {
        if (group == null) {
            return null;
        }

        List<ReadingOrderDto.GroupMemberDto> members = new ArrayList<>();
        for (int i = 0; i < group.getSize(); i++) {
            GroupMember member = group.getMember(i);
            Integer memberIndex = group.isOrdered() ? i : null;
            if (member instanceof RegionRef regionRef) {
                members.add(new ReadingOrderDto.RegionRefDto(
                    null,
                    regionRef.getRegionId() != null ? regionRef.getRegionId().toString() : null,
                    memberIndex
                ));
            } else if (member instanceof Group childGroup) {
                members.add(new ReadingOrderDto.NestedGroupDto(toGroupDto(childGroup, memberIndex)));
            }
        }

        return new ReadingOrderDto.GroupDto(
            group.getId() != null ? group.getId().toString() : null,
            group.isOrdered(),
            index,
            getStringAttr(group, DefaultXmlNames.ATTR_caption),
            getStringAttr(group, DefaultXmlNames.ATTR_type),
            group.getRegionRef() != null ? group.getRegionRef().toString() : null,
            members,
            getBooleanAttr(group, DefaultXmlNames.ATTR_continuation),
            toUserDefinedDto(group.getUserDefinedAttributes()),
            toLabelsDtos(group.getLabels()),
            getStringAttr(group, DefaultXmlNames.ATTR_custom),
            getStringAttr(group, DefaultXmlNames.ATTR_comments)
        );
    }

    private LayersDto toLayersDto(Layers layers) {
        if (layers == null || layers.getSize() == 0) {
            return null;
        }
        List<LayerDto> layerDtos = new ArrayList<>();
        for (int i = 0; i < layers.getSize(); i++) {
            Layer layer = layers.getLayer(i);
            if (layer == null) {
                continue;
            }
            List<String> regionRefs = new ArrayList<>();
            for (int m = 0; m < layer.getSize(); m++) {
                GroupMember member = layer.getMember(m);
                if (member instanceof RegionRef ref && ref.getRegionId() != null) {
                    regionRefs.add(ref.getRegionId().toString());
                }
            }
            layerDtos.add(new LayerDto(
                layer.getId() != null ? layer.getId().toString() : null,
                layer.getZIndex(),
                getStringAttr(layer, DefaultXmlNames.ATTR_caption),
                regionRefs.isEmpty() ? null : regionRefs
            ));
        }
        return layerDtos.isEmpty() ? null : new LayersDto(layerDtos);
    }

    private RelationsDto toRelationsDto(Relations relations) {
        if (relations == null || relations.isEmpty()) {
            return null;
        }
        List<RelationDto> relationDtos = relations.exportRelations().stream()
            .filter(relation -> relation != null)
            .sorted(Comparator.comparing(relation -> relation.getId() != null ? relation.getId().toString() : ""))
            .map(relation -> new RelationDto(
                relation.getId() != null ? relation.getId().toString() : null,
                relation.getRelationType() != null ? relation.getRelationType().toString() : null,
                relation.getObject1() != null && relation.getObject1().getId() != null ? relation.getObject1().getId().toString() : null,
                relation.getObject2() != null && relation.getObject2().getId() != null ? relation.getObject2().getId().toString() : null,
                nullIfEmpty(relation.getCustomField()),
                nullIfEmpty(relation.getComments()),
                toLabelsDtos(relation.getLabels())
            ))
            .toList();
        return relationDtos.isEmpty() ? null : new RelationsDto(relationDtos);
    }

    private List<AlternativeImageDto> toAlternativeImageDtos(List<AlternativeImage> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<AlternativeImageDto> dtos = new ArrayList<>();
        for (AlternativeImage image : images) {
            if (image == null) {
                continue;
            }
            dtos.add(new AlternativeImageDto(
                nullIfEmpty(image.getFilename()),
                nullIfEmpty(image.getComments()),
                image.getConfidence()
            ));
        }
        return dtos.isEmpty() ? null : dtos;
    }

    private List<LabelsDto> toLabelsDtos(Labels labels) {
        if (labels == null || labels.getGroups() == null || labels.getGroups().isEmpty()) {
            return null;
        }
        List<LabelsDto> groups = new ArrayList<>();
        for (Map.Entry<String, LabelGroup> entry : labels.getGroups().entrySet()) {
            LabelGroup group = entry.getValue();
            if (group == null) {
                continue;
            }
            List<LabelDto> groupLabels = new ArrayList<>();
            for (Label label : group.getLabels()) {
                if (label == null) {
                    continue;
                }
                groupLabels.add(new LabelDto(
                    nullIfEmpty(label.getValue()),
                    nullIfEmpty(label.getType()),
                    nullIfEmpty(label.getComments())
                ));
            }
            groups.add(new LabelsDto(
                nullIfEmpty(group.getExternalModel()),
                nullIfEmpty(group.getExternalId()),
                nullIfEmpty(group.getPrefix()),
                nullIfEmpty(group.getComments()),
                groupLabels.isEmpty() ? null : groupLabels
            ));
        }
        return groups.isEmpty() ? null : groups;
    }

    private UserDefinedDto toUserDefinedDto(VariableMap attributes) {
        if (attributes == null || attributes.getSize() == 0) {
            return null;
        }
        List<UserAttributeDto> userAttributes = new ArrayList<>();
        for (int i = 0; i < attributes.getSize(); i++) {
            Variable variable = attributes.get(i);
            if (variable == null) {
                continue;
            }
            userAttributes.add(new UserAttributeDto(
                nullIfEmpty(variable.getName()),
                nullIfEmpty(variable.getDescription()),
                toUserAttributeType(variable),
                variable.getValue() != null ? nullIfEmpty(variable.getValue().toString()) : null
            ));
        }
        return userAttributes.isEmpty() ? null : new UserDefinedDto(userAttributes);
    }

    private String toUserAttributeType(Variable variable) {
        if (variable instanceof StringVariable) {
            return "xsd:string";
        }
        if (variable instanceof IntegerVariable) {
            return "xsd:integer";
        }
        if (variable instanceof DoubleVariable) {
            return "xsd:float";
        }
        if (variable instanceof com.maxnth.page4j.basic.variable.BooleanVariable) {
            return "xsd:boolean";
        }
        return null;
    }

    private TextStyleDto toTextStyleDto(AttributeContainer container) {
        if (container == null) {
            return null;
        }
        TextStyleDto style = new TextStyleDto(
            getStringAttr(container, "fontFamily"),
            getBooleanAttr(container, "serif"),
            getBooleanAttr(container, "monospace"),
            getDoubleAttr(container, DefaultXmlNames.ATTR_fontSize),
            getIntegerAttr(container, DefaultXmlNames.ATTR_xHeight),
            getIntegerAttr(container, DefaultXmlNames.ATTR_kerning),
            getStringAttr(container, DefaultXmlNames.ATTR_textColour),
            getIntegerAttr(container, DefaultXmlNames.ATTR_textColourRgb),
            getStringAttr(container, DefaultXmlNames.ATTR_bgColour),
            getIntegerAttr(container, "bgColourRgb"),
            getBooleanAttr(container, DefaultXmlNames.ATTR_reverseVideo),
            getBooleanAttr(container, DefaultXmlNames.ATTR_bold),
            getBooleanAttr(container, DefaultXmlNames.ATTR_italic),
            getBooleanAttr(container, DefaultXmlNames.ATTR_underlined),
            getStringAttr(container, DefaultXmlNames.ATTR_underlineStyle),
            getBooleanAttr(container, DefaultXmlNames.ATTR_subscript),
            getBooleanAttr(container, DefaultXmlNames.ATTR_superscript),
            getBooleanAttr(container, DefaultXmlNames.ATTR_strikethrough),
            getBooleanAttr(container, DefaultXmlNames.ATTR_smallCaps),
            getBooleanAttr(container, DefaultXmlNames.ATTR_letterSpaced)
        );
        return isEmpty(style) ? null : style;
    }

    private boolean isEmpty(TextStyleDto style) {
        return style.fontFamily() == null
            && style.serif() == null
            && style.monospace() == null
            && style.fontSize() == null
            && style.xHeight() == null
            && style.kerning() == null
            && style.textColour() == null
            && style.textColourRgb() == null
            && style.bgColour() == null
            && style.bgColourRgb() == null
            && style.reverseVideo() == null
            && style.bold() == null
            && style.italic() == null
            && style.underlined() == null
            && style.underlineStyle() == null
            && style.subscript() == null
            && style.superscript() == null
            && style.strikethrough() == null
            && style.smallCaps() == null
            && style.letterSpaced() == null;
    }

    private String getStringAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        Variable variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof StringValue value) {
            return nullIfEmpty(value.val);
        }
        return nullIfEmpty(variable.getValue().toString());
    }

    private Double getDoubleAttr(Object obj, String attrName) {
        if (!isAttributePresent(obj, attrName)) {
            return null;
        }
        VariableMap attrs = getAttributes(obj);
        if (attrs == null) {
            return null;
        }
        Variable variable = attrs.get(attrName);
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
            try {
                return Double.parseDouble(value.val);
            } catch (Exception ignored) {
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
        Variable variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof BooleanValue value) {
            return value.val;
        }
        if (variable.getValue() instanceof StringValue value) {
            return Boolean.parseBoolean(value.val);
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
        Variable variable = attrs.get(attrName);
        if (variable == null || variable.getValue() == null) {
            return null;
        }
        if (variable.getValue() instanceof IntegerValue value) {
            return value.val;
        }
        if (variable.getValue() instanceof StringValue value) {
            try {
                return Integer.parseInt(value.val);
            } catch (Exception ignored) {
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

    private List<String> mapLabelsToLabelIds(Page page) {
        return null;
    }

    private List<String> mapRegionLabelsToLabelIds(Region region) {
        return null;
    }
}
