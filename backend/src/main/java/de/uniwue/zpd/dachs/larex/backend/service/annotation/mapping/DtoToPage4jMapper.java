package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

import com.maxnth.page4j.basic.ident.IdRegister;
import com.maxnth.page4j.basic.labels.HasLabels;
import com.maxnth.page4j.basic.labels.LabelImpl;
import com.maxnth.page4j.basic.labels.LabelGroup;
import com.maxnth.page4j.basic.labels.Labels;
import com.maxnth.page4j.basic.variable.BooleanVariable;
import com.maxnth.page4j.basic.variable.IntegerVariable;
import com.maxnth.page4j.basic.variable.StringValue;
import com.maxnth.page4j.basic.variable.StringVariable;
import com.maxnth.page4j.basic.variable.Variable;
import com.maxnth.page4j.basic.variable.VariableMap;
import com.maxnth.page4j.basic.variable.VariableValue;
import com.maxnth.page4j.dla.page.AlternativeImage;
import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.layout.GeometricObjectImpl;
import com.maxnth.page4j.dla.page.layout.PageLayout;
import com.maxnth.page4j.dla.page.layout.logical.ContentObjectRelation;
import com.maxnth.page4j.dla.page.layout.logical.Group;
import com.maxnth.page4j.dla.page.layout.logical.GroupMember;
import com.maxnth.page4j.dla.page.layout.logical.Layer;
import com.maxnth.page4j.dla.page.layout.logical.Layers;
import com.maxnth.page4j.dla.page.layout.logical.ReadingOrder;
import com.maxnth.page4j.dla.page.layout.logical.Relations;
import com.maxnth.page4j.dla.page.layout.physical.ContentObject;
import com.maxnth.page4j.dla.page.layout.physical.Region;
import com.maxnth.page4j.dla.page.layout.physical.impl.TableGrid;
import com.maxnth.page4j.dla.page.layout.physical.impl.TableRegion;
import com.maxnth.page4j.dla.page.layout.physical.role.RegionRole;
import com.maxnth.page4j.dla.page.layout.physical.shared.ContentType;
import com.maxnth.page4j.dla.page.layout.physical.shared.LowLevelTextType;
import com.maxnth.page4j.dla.page.layout.physical.shared.RegionType;
import com.maxnth.page4j.dla.page.layout.physical.shared.RoleType;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextContainer;
import com.maxnth.page4j.dla.page.layout.physical.text.LowLevelTextObject;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContent;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContentVariants;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.Grapheme;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.GraphemeElement;
import com.maxnth.page4j.dla.page.layout.physical.text.graphemes.GraphemeGroup;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Glyph;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextLine;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextRegion;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.Word;
import com.maxnth.page4j.dla.page.metadata.MetaData;
import com.maxnth.page4j.dla.page.metadata.MetadataItem;
import com.maxnth.page4j.maths.geometry.Polygon;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.AlternativeImageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GlyphDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemeElementDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.LayerDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.LayersDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataItemDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.TableCellRoleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.style.TextStyleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.UserAttributeDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.UserDefinedDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DtoToPage4jMapper {

    public Page toPage4j(PageDto dto) {
        if (dto == null) {
            return null;
        }

        return new DtoToPage4jMappingSession(dto.imageWidth(), dto.imageHeight()).toPage4j(dto);
    }
}

final class DtoToPage4jMappingSession {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final int imageWidth;
    private final int imageHeight;

    DtoToPage4jMappingSession(int imageWidth, int imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    Page toPage4j(PageDto dto) {
        Page page = new Page(PageXmlInputOutput.getLatestSchemaModel());
        page.setImageFilename(dto.imageFilename());

        PageLayout layout = page.getLayout();
        layout.setSize(dto.imageWidth(), dto.imageHeight());

        if (dto.metadata() != null) {
            setMetadata(page.getMetaData(), dto.metadata());
        }

        if (dto.pcGtsId() != null) {
            try {
                page.setGtsId(dto.pcGtsId());
            } catch (IdRegister.InvalidIdException ignored) {
            }
        }

        setPageAttributes(page, dto);
        applyAlternativeImages(page.getAlternativeImages(), dto.alternativeImages());
        setLabels(page, dto.labels());
        page.setUserDefinedAttributes(toVariableMap(dto.userDefined()));
        applyTextStyle(page.getAttributes(), dto.textStyle());

        if (dto.border() != null) {
            layout.setBorder(new GeometricObjectImpl(toPolygon(dto.border())));
        }
        if (dto.printSpace() != null) {
            layout.setPrintSpace(new GeometricObjectImpl(toPolygon(dto.printSpace())));
        }

        if (dto.regions() != null) {
            for (RegionDto regionDto : dto.regions()) {
                addRegion(layout, null, regionDto);
            }
        }

        if (dto.readingOrder() != null) {
            setReadingOrder(layout, dto.readingOrder());
        }
        if (dto.layers() != null) {
            setLayers(layout, dto.layers());
        }
        if (dto.relations() != null) {
            setRelations(layout, dto.relations());
        }

        prunePageAttributes(page, dto);
        removeEmptyStringAttributes(page);
        return page;
    }

    private void setMetadata(MetaData metaData, MetadataDto dto) {
        metaData.setCreator(normalizeNullable(dto.creator()));
        if (hasText(dto.created())) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dto.created().trim(), ISO_FORMAT);
                metaData.setCreationTime(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (DateTimeParseException ignored) {
            }
        }
        if (hasText(dto.lastChange())) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(dto.lastChange().trim(), ISO_FORMAT);
                metaData.setLastModifiedTime(Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant()));
            } catch (DateTimeParseException ignored) {
            }
        }
        metaData.setComments(normalizeNullable(dto.comments()));
        metaData.setExternalRef(normalizeNullable(dto.externalRef()));
        metaData.setUserDefinedAttributes(toVariableMap(dto.userDefined()));

        if (dto.items() != null) {
            for (MetadataItemDto itemDto : dto.items()) {
                if (itemDto == null) {
                    continue;
                }
                MetadataItem item = metaData.addMetadataItem();
                setStringAttr(item.getAttributes(), DefaultXmlNames.ATTR_type, itemDto.type());
                setStringAttr(item.getAttributes(), DefaultXmlNames.ATTR_name, itemDto.name());
                setStringAttr(item.getAttributes(), DefaultXmlNames.ATTR_value, itemDto.value());
                setStringAttr(item.getAttributes(), DefaultXmlNames.ATTR_date, itemDto.date());
                setLabels(item, itemDto.labels());
            }
        }
    }

    private void addRegion(PageLayout layout, Region parent, RegionDto dto) {
        if (dto == null) {
            return;
        }
        Region region = parent == null
            ? layout.createRegion(toRegionType(dto.kind()), dto.id())
            : layout.createRegion(toRegionType(dto.kind()), dto.id(), parent);

        if (dto.coords() != null) {
            region.setCoords(toPolygon(dto.coords()));
        }

        setRegionCommonAttributes(region, dto);
        applyAlternativeImages(region.getAlternativeImages(), dto.alternativeImages());
        setLabels(region, dto.labels());
        region.setUserDefinedAttributes(toVariableMap(dto.userDefined()));
        applyTextStyle(region.getAttributes(), dto.textStyle());
        setRoles(region, dto.tableCellRoleFromRoles());
        setGrid(region, dto.grid());

        if (region instanceof TextRegion textRegion) {
            setTextRegionAttributes(textRegion, dto);
            if (dto.textLines() != null) {
                for (TextLineDto lineDto : dto.textLines()) {
                    addTextLine(textRegion, lineDto);
                }
            }
            setTextContentVariants(textRegion, dto.textContentVariants());
        }

        pruneRegionAttributes(region, dto);

        if (dto.nestedRegions() != null) {
            for (RegionDto nested : dto.nestedRegions()) {
                addRegion(layout, region, nested);
            }
        }

    }

    private void setRoles(Region region, TableCellRoleDto roleDto) {
        if (region == null || roleDto == null) {
            return;
        }
        RegionRole role = region.addRole(RoleType.TableCellRole);
        if (role == null) {
            return;
        }
        setIntegerAttr(role.getAttributes(), DefaultXmlNames.ATTR_rowIndex, roleDto.rowIndex());
        setIntegerAttr(role.getAttributes(), DefaultXmlNames.ATTR_columnIndex, roleDto.columnIndex());
        setIntegerAttr(role.getAttributes(), DefaultXmlNames.ATTR_rowSpan, roleDto.rowSpan());
        setIntegerAttr(role.getAttributes(), DefaultXmlNames.ATTR_colSpan, roleDto.colSpan());
        setBooleanAttr(role.getAttributes(), DefaultXmlNames.ATTR_header, roleDto.header());
    }

    private void setGrid(Region region, GridDto gridDto) {
        if (!(region instanceof TableRegion tableRegion) || gridDto == null) {
            return;
        }
        TableGrid grid = new TableGrid();
        tableRegion.setGrid(grid);

        if (gridDto.rows() == null || gridDto.rows().isEmpty()) {
            return;
        }

        List<GridPointsDto> rows = new ArrayList<>(gridDto.rows());
        rows.sort(Comparator.comparing(r -> r.index() != null ? r.index() : Integer.MAX_VALUE));
        try {
            Field rowsField = TableGrid.class.getDeclaredField("rows");
            rowsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<TableGrid.TableGridRow> rowList = (List<TableGrid.TableGridRow>) rowsField.get(grid);
            for (GridPointsDto rowDto : rows) {
                if (rowDto == null) {
                    continue;
                }
                TableGrid.TableGridRow row = new TableGrid.TableGridRow();
                row.setCoords(toPolygon(rowDto.points()));
                rowList.add(row);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private void setTextRegionAttributes(TextRegion textRegion, RegionDto dto) {
        setTextRegionTypeAttr(textRegion.getAttributes(), dto.type());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_orientation, dto.orientation());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_textColour, dto.textColour());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_bgColour, dto.bgColour());
        setBooleanAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_reverseVideo, dto.reverseVideo());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_fontSize, dto.fontSize());
        setStringAttr(textRegion.getAttributes(), "fontFamily", dto.fontFamily());
        setBooleanAttr(textRegion.getAttributes(), "serif", dto.serif());
        setBooleanAttr(textRegion.getAttributes(), "monospace", dto.monospace());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_xHeight, dto.xHeight());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_leading, dto.leading());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_kerning, dto.kerning());
        setStringAttr(textRegion.getAttributes(), "align", dto.align());
        setIntegerAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_textColourRgb, dto.textColourRgb());
        setIntegerAttr(textRegion.getAttributes(), "bgColourRgb", dto.bgColourRgb());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setDoubleAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_readingOrientation, dto.readingOrientation());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
        setBooleanAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_indented, dto.indented());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        setStringAttr(textRegion.getAttributes(), DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        setStringAttr(textRegion.getAttributes(), "production", dto.production());
        applyTextStyle(textRegion.getAttributes(), dto.textStyle());
    }

    private void setTextRegionTypeAttr(VariableMap attributes, String value) {
        if (attributes == null) {
            return;
        }
        String normalized = normalizeNullable(value);
        if (normalized != null && "custom".equalsIgnoreCase(normalized)) {
            normalized = "other";
        }
        if (normalized == null) {
            removeAttribute(attributes, DefaultXmlNames.ATTR_type);
            return;
        }
        Variable variable = findVariable(attributes, DefaultXmlNames.ATTR_type);
        if (variable == null) {
            return;
        }
        variable.parseValue(normalized);
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

        setTextContentVariants(textLine, dto.textContentVariants());
        applyAlternativeImages(textLine.getAlternativeImages(), dto.alternativeImages());
        setLabels(textLine, dto.labels());
        textLine.setUserDefinedAttributes(toVariableMap(dto.userDefined()));

        applyTextStyle(textLine.getAttributes(), dto.textStyle());
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        setStringAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        setBooleanAttr(textLine.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
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

        setTextContentVariants(word, dto.textContentVariants());
        applyAlternativeImages(word.getAlternativeImages(), dto.alternativeImages());
        setLabels(word, dto.labels());
        word.setUserDefinedAttributes(toVariableMap(dto.userDefined()));
        applyTextStyle(word.getAttributes(), dto.textStyle());

        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        setBooleanAttr(word.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_language, dto.language());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_script, firstNonBlank(dto.script(), dto.primaryScript()));
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        setStringAttr(word.getAttributes(), "production", dto.production());
        setDoubleAttr(word.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(word.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());

        pruneWordAttributes(word, dto);

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

        setTextContentVariants(glyph, dto.textContentVariants());
        applyAlternativeImages(glyph.getAlternativeImages(), dto.alternativeImages());
        setLabels(glyph, dto.labels());
        glyph.setUserDefinedAttributes(toVariableMap(dto.userDefined()));
        applyTextStyle(glyph.getAttributes(), dto.textStyle());

        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_ligature, dto.ligature());
        setBooleanAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_symbol, dto.symbol());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_script, dto.script());
        setStringAttr(glyph.getAttributes(), "production", dto.production());
        setDoubleAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(glyph.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());

        addGraphemes(glyph, dto.graphemes());
        pruneGlyphAttributes(glyph, dto);
    }

    private void addGraphemes(Glyph glyph, GraphemesDto graphemesDto) {
        if (glyph == null || graphemesDto == null || graphemesDto.elements() == null) {
            return;
        }
        List<GraphemeElementDto> elements = new ArrayList<>(graphemesDto.elements());
        elements.sort(Comparator.comparing(e -> e.index() != null ? e.index() : Integer.MAX_VALUE));
        for (GraphemeElementDto dto : elements) {
            addGraphemeElement(glyph, null, dto);
        }
    }

    private void addGraphemeElement(Glyph glyph, GraphemeGroup parentGroup, GraphemeElementDto dto) {
        if (glyph == null || dto == null) {
            return;
        }
        LowLevelTextType type = toGraphemeType(dto.kind());
        GraphemeElement element = glyph.createGraphemeElement(dto.id(), type, parentGroup);
        if (element == null) {
            return;
        }

        setIntegerAttr(element.getAttributes(), DefaultXmlNames.ATTR_index, dto.index());
        setStringAttr(element.getAttributes(), DefaultXmlNames.ATTR_charType, dto.charType());
        setBooleanAttr(element.getAttributes(), DefaultXmlNames.ATTR_ligature, dto.ligature());
        setStringAttr(element.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(element.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        setTextContentVariants(element, dto.textContentVariants());

        if (element instanceof Grapheme grapheme) {
            if (dto.coords() != null) {
                grapheme.setCoords(toPolygon(dto.coords()));
            }
            setLabels(grapheme, dto.labels());
        }

        if (element instanceof GraphemeGroup group && dto.members() != null) {
            List<GraphemeElementDto> members = new ArrayList<>(dto.members());
            members.sort(Comparator.comparing(e -> e.index() != null ? e.index() : Integer.MAX_VALUE));
            for (GraphemeElementDto member : members) {
                addGraphemeElement(glyph, group, member);
            }
        }
    }

    private LowLevelTextType toGraphemeType(String kind) {
        if ("graphemeGroup".equals(kind)) {
            return LowLevelTextType.GraphemeGroup;
        }
        if ("nonPrintingChar".equals(kind)) {
            return LowLevelTextType.NonPrintingCharacter;
        }
        return LowLevelTextType.Grapheme;
    }

    private void setTextContentVariants(TextContentVariants textObj, List<TextContentVariantDto> variants) {
        if (textObj == null || variants == null || variants.isEmpty()) {
            return;
        }

        TextContentVariantDto first = variants.get(0);
        if (hasText(first.unicode())) {
            textObj.setText(first.unicode());
        }
        if (hasText(first.plainText())) {
            textObj.setPlainText(first.plainText());
        }
        if (first.confidence() != null) {
            textObj.setConfidence(first.confidence());
        }
        if (hasText(first.dataType())) {
            textObj.setDataType(first.dataType());
        }
        if (hasText(first.dataTypeDetails())) {
            textObj.setDataTypeDetails(first.dataTypeDetails());
        }
        if (hasText(first.comments())) {
            textObj.setComments(first.comments());
        }
        setTextContentIndex(textObj.getTextContentVariant(0), first.index());

        for (int i = 1; i < variants.size(); i++) {
            TextContentVariantDto v = variants.get(i);
            TextContent tc = textObj.addTextContentVariant();
            if (hasText(v.unicode())) tc.setText(v.unicode());
            if (hasText(v.plainText())) tc.setPlainText(v.plainText());
            if (v.confidence() != null) tc.setConfidence(v.confidence());
            if (hasText(v.dataType())) tc.setDataType(v.dataType());
            if (hasText(v.dataTypeDetails())) tc.setDataTypeDetails(v.dataTypeDetails());
            if (hasText(v.comments())) tc.setComments(v.comments());
            setTextContentIndex(tc, v.index());
        }
    }

    private void setTextContentIndex(TextContent tc, Integer index) {
        if (tc == null || tc.getAttributes() == null) return;
        Variable indexVar = findVariable(tc.getAttributes(), DefaultXmlNames.ATTR_index);
        if (index == null) {
            removeAttribute(tc.getAttributes(), DefaultXmlNames.ATTR_index);
            return;
        }
        if (indexVar == null) {
            indexVar = new IntegerVariable(DefaultXmlNames.ATTR_index);
            tc.getAttributes().add(indexVar);
        }
        try {
            indexVar.setValue(VariableValue.of(index));
        } catch (Variable.WrongVariableTypeException ignored) {
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
        populateGroup(readingOrder.getRoot(), dto.root());
    }

    private void populateGroup(Group group, ReadingOrderDto.GroupDto dto) {
        if (group == null || dto == null) {
            return;
        }

        group.setOrdered(dto.ordered());
        if (hasText(dto.caption())) {
            group.setCaption(dto.caption());
        }
        if (hasText(dto.regionRef())) {
            group.setRegionRef(dto.regionRef());
        }
        setStringAttr(group.getAttributes(), DefaultXmlNames.ATTR_type, dto.groupType());
        setBooleanAttr(group.getAttributes(), DefaultXmlNames.ATTR_continuation, dto.continuation());
        setStringAttr(group.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(group.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        group.setUserDefinedAttributes(toVariableMap(dto.userDefined()));
        setLabels(group, dto.labels());

        if (dto.members() != null) {
            for (ReadingOrderDto.GroupMemberDto member : dto.members()) {
                if (member instanceof ReadingOrderDto.RegionRefDto refDto) {
                    if (hasText(refDto.regionRef())) {
                        group.addRegionRef(refDto.regionRef());
                    }
                } else if (member instanceof ReadingOrderDto.NestedGroupDto nestedDto) {
                    try {
                        Group child = group.createChildGroup();
                        populateGroup(child, nestedDto.group());
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        pruneGroupAttributes(group, dto);
    }

    private void setLayers(PageLayout layout, LayersDto dto) {
        if (dto == null || dto.layers() == null || dto.layers().isEmpty()) {
            return;
        }
        Layers layers = layout.createLayers();
        layers.setManageZIndexes(false);
        for (LayerDto layerDto : dto.layers()) {
            if (layerDto == null) {
                continue;
            }
            Layer layer = layers.createLayer(true);
            if (layer == null) {
                continue;
            }
            if (hasText(layerDto.id())) {
                try {
                    layer.setId(layerDto.id());
                } catch (Exception ignored) {
                }
            }
            if (layerDto.zIndex() != null) {
                layer.setZIndex(layerDto.zIndex());
            }
            if (hasText(layerDto.caption())) {
                layer.setCaption(layerDto.caption());
            }
            if (layerDto.regionRefs() != null) {
                for (String regionRef : layerDto.regionRefs()) {
                    if (hasText(regionRef)) {
                        layer.addRegionRef(regionRef);
                    }
                }
            }
        }
        layers.sort();
    }

    private void setRelations(PageLayout layout, RelationsDto dto) {
        if (dto == null || dto.relations() == null || dto.relations().isEmpty()) {
            return;
        }
        Relations relations = layout.getRelations();
        for (RelationDto relationDto : dto.relations()) {
            if (relationDto == null) {
                continue;
            }
            Region source = findRegion(layout, relationDto.sourceRegionRef());
            Region target = findRegion(layout, relationDto.targetRegionRef());
            if (source == null || target == null) {
                continue;
            }
            ContentObjectRelation relation = relations.addRelation(
                source,
                target,
                toRelationType(relationDto.type()),
                relationDto.id()
            );
            if (relation == null) {
                continue;
            }
            relation.setCustomField(normalizeNullable(relationDto.custom()));
            relation.setComments(normalizeNullable(relationDto.comments()));
            setLabels(relation, relationDto.labels());
        }
    }

    private Region findRegion(PageLayout layout, String id) {
        if (layout == null || !hasText(id)) {
            return null;
        }
        Region direct = layout.getRegion(id);
        if (direct != null) {
            return direct;
        }
        for (int i = 0; i < layout.getRegionCount(); i++) {
            Region nested = findRegion(layout.getRegion(i), id);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private Region findRegion(Region parent, String id) {
        if (parent == null || !hasText(id)) {
            return null;
        }
        if (parent.getId() != null && id.equals(parent.getId().toString())) {
            return parent;
        }
        for (int i = 0; i < parent.getRegionCount(); i++) {
            Region found = findRegion(parent.getRegion(i), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ContentObjectRelation.RelationType toRelationType(String type) {
        if ("join".equalsIgnoreCase(type)) {
            return ContentObjectRelation.RelationType.Join;
        }
        if ("ParentChildRelation".equalsIgnoreCase(type)) {
            return ContentObjectRelation.RelationType.ParentChildRelation;
        }
        return ContentObjectRelation.RelationType.Link;
    }

    private Polygon toPolygon(PolygonDto dto) {
        if (dto == null || dto.points() == null || dto.points().isEmpty()) {
            return null;
        }
        Polygon polygon = new Polygon();
        for (PointDto point : dto.points()) {
            int x = CoordinateUtils.worldToPixelX(point.x(), imageWidth);
            int y = CoordinateUtils.worldToPixelY(point.y(), imageHeight);
            polygon.addPoint(x, y);
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

    private void setRegionCommonAttributes(Region region, RegionDto dto) {
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_custom, dto.custom());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_comments, dto.comments());
        setBooleanAttr(region.getAttributes(), DefaultXmlNames.ATTR_continuation, dto.continuation());
        setDoubleAttr(region.getAttributes(), DefaultXmlNames.ATTR_conf, dto.confidence());
        if (!(region instanceof TextRegion)) {
            setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_type, dto.type());
        }
        setDoubleAttr(region.getAttributes(), DefaultXmlNames.ATTR_orientation, dto.orientation());
        setIntegerAttr(region.getAttributes(), DefaultXmlNames.ATTR_numColours, dto.numColours());
        setBooleanAttr(region.getAttributes(), DefaultXmlNames.ATTR_embText, dto.embText());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_colourDepth, dto.colourDepth());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_lineColour, dto.lineColour());
        setBooleanAttr(region.getAttributes(), DefaultXmlNames.ATTR_lineSeparators, dto.lineSeparators());
        setIntegerAttr(region.getAttributes(), DefaultXmlNames.ATTR_rows, dto.rows());
        setIntegerAttr(region.getAttributes(), DefaultXmlNames.ATTR_columns, dto.columns());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_colour, dto.colour());
        setStringAttr(region.getAttributes(), DefaultXmlNames.ATTR_penColour, dto.penColour());
        setBooleanAttr(region.getAttributes(), DefaultXmlNames.ATTR_borderPresent, dto.borderPresent());
    }

    private void applyAlternativeImages(List<AlternativeImage> target, List<AlternativeImageDto> source) {
        if (target == null) {
            return;
        }
        target.clear();
        if (source == null) {
            return;
        }
        for (AlternativeImageDto dto : source) {
            if (dto == null || !hasText(dto.filename())) {
                continue;
            }
            AlternativeImage image = new AlternativeImage(dto.filename());
            image.setComments(normalizeNullable(dto.comments()));
            image.setConfidence(dto.confidence());
            target.add(image);
        }
    }

    private void setLabels(HasLabels target, List<LabelsDto> labelGroups) {
        if (target == null) {
            return;
        }
        if (labelGroups == null || labelGroups.isEmpty()) {
            target.setLabels(null);
            return;
        }
        Labels labels = new Labels();
        for (LabelsDto groupDto : labelGroups) {
            if (groupDto == null) {
                continue;
            }
            LabelGroup group = new LabelGroup(normalizeNullable(groupDto.externalModel()));
            group.setExternalId(normalizeNullable(groupDto.externalId()));
            group.setPrefix(normalizeNullable(groupDto.prefix()));
            group.setComments(normalizeNullable(groupDto.comments()));
            if (groupDto.labels() != null) {
                for (LabelDto labelDto : groupDto.labels()) {
                    if (labelDto == null || !hasText(labelDto.value())) {
                        continue;
                    }
                    LabelImpl label = new LabelImpl(labelDto.value(), group.getExternalModel());
                    label.setType(normalizeNullable(labelDto.type()));
                    label.setComments(normalizeNullable(labelDto.comments()));
                    group.addLabel(label);
                }
            }
            labels.addGroup(group);
        }
        target.setLabels(labels);
    }

    private VariableMap toVariableMap(UserDefinedDto dto) {
        if (dto == null || dto.attributes() == null || dto.attributes().isEmpty()) {
            return null;
        }
        VariableMap map = new VariableMap();
        for (UserAttributeDto attr : dto.attributes()) {
            if (attr == null || !hasText(attr.name())) {
                continue;
            }
            Variable variable = createUserVariable(attr);
            if (hasText(attr.description())) {
                variable.setDescription(attr.description().trim());
            }
            if (hasText(attr.value())) {
                variable.parseValue(attr.value().trim());
            }
            map.add(variable);
        }
        return map.getSize() == 0 ? null : map;
    }

    private Variable createUserVariable(UserAttributeDto attr) {
        String type = normalizeNullable(attr.type());
        String name = attr.name().trim();
        if ("xsd:integer".equals(type)) {
            return new com.maxnth.page4j.basic.variable.IntegerVariable(name);
        }
        if ("xsd:float".equals(type)) {
            return new com.maxnth.page4j.basic.variable.DoubleVariable(name);
        }
        if ("xsd:boolean".equals(type)) {
            return new BooleanVariable(name);
        }
        return new StringVariable(name);
    }

    private void applyTextStyle(VariableMap attributes, TextStyleDto style) {
        if (attributes == null || style == null) {
            return;
        }
        setStringAttr(attributes, "fontFamily", style.fontFamily());
        setBooleanAttr(attributes, "serif", style.serif());
        setBooleanAttr(attributes, "monospace", style.monospace());
        setDoubleAttr(attributes, DefaultXmlNames.ATTR_fontSize, style.fontSize());
        setIntegerAttr(attributes, DefaultXmlNames.ATTR_xHeight, style.xHeight());
        setIntegerAttr(attributes, DefaultXmlNames.ATTR_kerning, style.kerning());
        setStringAttr(attributes, DefaultXmlNames.ATTR_textColour, style.textColour());
        setIntegerAttr(attributes, DefaultXmlNames.ATTR_textColourRgb, style.textColourRgb());
        setStringAttr(attributes, DefaultXmlNames.ATTR_bgColour, style.bgColour());
        setIntegerAttr(attributes, "bgColourRgb", style.bgColourRgb());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_reverseVideo, style.reverseVideo());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_bold, style.bold());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_italic, style.italic());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_underlined, style.underlined());
        setStringAttr(attributes, DefaultXmlNames.ATTR_underlineStyle, style.underlineStyle());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_subscript, style.subscript());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_superscript, style.superscript());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_strikethrough, style.strikethrough());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_smallCaps, style.smallCaps());
        setBooleanAttr(attributes, DefaultXmlNames.ATTR_letterSpaced, style.letterSpaced());
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
        if (dto.textStyle() != null) {
            addTextStyleAllowed(allowed, dto.textStyle());
        }
        pruneAttributes(page.getAttributes(), allowed);
    }

    private void pruneRegionAttributes(Region region, RegionDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_continuation, dto.continuation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_type, dto.type());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_orientation, dto.orientation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_numColours, dto.numColours());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_embText, dto.embText());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_colourDepth, dto.colourDepth());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_lineColour, dto.lineColour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_lineSeparators, dto.lineSeparators());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_rows, dto.rows());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_columns, dto.columns());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_colour, dto.colour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_penColour, dto.penColour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_borderPresent, dto.borderPresent());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textColour, dto.textColour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bgColour, dto.bgColour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_reverseVideo, dto.reverseVideo());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_fontSize, dto.fontSize());
        addAllowedIfPresent(allowed, "fontFamily", dto.fontFamily());
        addAllowedIfPresent(allowed, "serif", dto.serif());
        addAllowedIfPresent(allowed, "monospace", dto.monospace());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_xHeight, dto.xHeight());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_leading, dto.leading());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_kerning, dto.kerning());
        addAllowedIfPresent(allowed, "align", dto.align());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textColourRgb, dto.textColourRgb());
        addAllowedIfPresent(allowed, "bgColourRgb", dto.bgColourRgb());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingOrientation, dto.readingOrientation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textLineOrder, dto.textLineOrder());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_indented, dto.indented());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryLanguage, dto.secondaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        addAllowedIfPresent(allowed, "production", dto.production());
        if (dto.textStyle() != null) {
            addTextStyleAllowed(allowed, dto.textStyle());
        }
        pruneAttributes(region.getAttributes(), allowed);
    }

    private void pruneTextLineAttributes(TextLine textLine, TextLineDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryLanguage, dto.primaryLanguage());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_index, dto.index());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        if (dto.textStyle() != null) {
            addTextStyleAllowed(allowed, dto.textStyle());
        }
        pruneAttributes(textLine.getAttributes(), allowed);
    }

    private void pruneWordAttributes(Word word, WordDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_language, dto.language());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_primaryScript, dto.primaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_secondaryScript, dto.secondaryScript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_script, firstNonBlank(dto.script(), dto.primaryScript()));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_readingDirection, dto.readingDirection());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        if (dto.textStyle() != null) {
            addTextStyleAllowed(allowed, dto.textStyle());
        }
        pruneAttributes(word.getAttributes(), allowed);
    }

    private void pruneGlyphAttributes(Glyph glyph, GlyphDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, firstNonNull(dto.bold(), dto.textStyle() != null ? dto.textStyle().bold() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, firstNonNull(dto.italic(), dto.textStyle() != null ? dto.textStyle().italic() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, firstNonNull(dto.underlined(), dto.textStyle() != null ? dto.textStyle().underlined() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, firstNonBlank(dto.underlineStyle(), dto.textStyle() != null ? dto.textStyle().underlineStyle() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, firstNonNull(dto.subscript(), dto.textStyle() != null ? dto.textStyle().subscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, firstNonNull(dto.superscript(), dto.textStyle() != null ? dto.textStyle().superscript() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, firstNonNull(dto.strikethrough(), dto.textStyle() != null ? dto.textStyle().strikethrough() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, firstNonNull(dto.smallCaps(), dto.textStyle() != null ? dto.textStyle().smallCaps() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, firstNonNull(dto.letterSpaced(), dto.textStyle() != null ? dto.textStyle().letterSpaced() : null));
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_ligature, dto.ligature());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_symbol, dto.symbol());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_script, dto.script());
        addAllowedIfPresent(allowed, "production", dto.production());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_conf, dto.confidence());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        if (dto.textStyle() != null) {
            addTextStyleAllowed(allowed, dto.textStyle());
        }
        pruneAttributes(glyph.getAttributes(), allowed);
    }

    private void pruneGroupAttributes(Group group, ReadingOrderDto.GroupDto dto) {
        Set<String> allowed = new HashSet<>();
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_caption, dto.caption());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_type, dto.groupType());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_continuation, dto.continuation());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_custom, dto.custom());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_comments, dto.comments());
        pruneAttributes(group.getAttributes(), allowed);
    }

    private void addTextStyleAllowed(Set<String> allowed, TextStyleDto style) {
        addAllowedIfPresent(allowed, "fontFamily", style.fontFamily());
        addAllowedIfPresent(allowed, "serif", style.serif());
        addAllowedIfPresent(allowed, "monospace", style.monospace());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_fontSize, style.fontSize());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_xHeight, style.xHeight());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_kerning, style.kerning());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textColour, style.textColour());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_textColourRgb, style.textColourRgb());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bgColour, style.bgColour());
        addAllowedIfPresent(allowed, "bgColourRgb", style.bgColourRgb());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_reverseVideo, style.reverseVideo());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_bold, style.bold());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_italic, style.italic());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlined, style.underlined());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_underlineStyle, style.underlineStyle());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_subscript, style.subscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_superscript, style.superscript());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_strikethrough, style.strikethrough());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_smallCaps, style.smallCaps());
        addAllowedIfPresent(allowed, DefaultXmlNames.ATTR_letterSpaced, style.letterSpaced());
    }

    private <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return (second != null && !second.isBlank()) ? second : null;
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
            Variable variable = attributes.get(i);
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
        Variable variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        variable.parseValue(coerceStringValue(attrName, normalized));
    }

    private void setBooleanAttr(VariableMap attributes, String attrName, Boolean value) {
        if (attributes == null) {
            return;
        }
        if (value == null) {
            removeAttribute(attributes, attrName);
            return;
        }
        Variable variable = findVariable(attributes, attrName);
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
        Variable variable = findVariable(attributes, attrName);
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
        Variable variable = findVariable(attributes, attrName);
        if (variable == null) {
            return;
        }
        variable.parseValue(Double.toString(value));
    }

    private Variable findVariable(VariableMap attributes, String attrName) {
        if (attributes == null || attrName == null) {
            return null;
        }
        Variable byName = attributes.get(attrName);
        if (byName != null) {
            return byName;
        }
        for (int i = 0; i < attributes.getSize(); i++) {
            Variable variable = attributes.get(i);
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
            }
        }
        return trimmed;
    }

    private void removeAttribute(VariableMap attributes, String attrName) {
        if (attributes == null || attrName == null) {
            return;
        }
        for (int i = attributes.getSize() - 1; i >= 0; i--) {
            Variable variable = attributes.get(i);
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

        PageLayout layout = page.getLayout();
        if (layout != null) {
            for (int i = 0; i < layout.getRegionCount(); i++) {
                sanitizeRegion(layout.getRegion(i));
            }
            ReadingOrder readingOrder = layout.getReadingOrder();
            if (readingOrder != null && readingOrder.getRoot() != null) {
                sanitizeGroup(readingOrder.getRoot());
            }
        }
    }

    private void sanitizeGroup(Group group) {
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
            Variable variable = attributes.get(i);
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
