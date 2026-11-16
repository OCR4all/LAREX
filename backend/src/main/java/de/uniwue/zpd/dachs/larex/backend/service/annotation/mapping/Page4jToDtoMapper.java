package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

import com.maxnth.page4j.geometry.Point;
import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.AdvertRegion;
import com.maxnth.page4j.model.AlternativeImage;
import com.maxnth.page4j.model.ChartRegion;
import com.maxnth.page4j.model.ChemRegion;
import com.maxnth.page4j.model.CustomRegion;
import com.maxnth.page4j.model.FormRegion;
import com.maxnth.page4j.model.FrameRegion;
import com.maxnth.page4j.model.Glyph;
import com.maxnth.page4j.model.Grapheme;
import com.maxnth.page4j.model.GraphemeElement;
import com.maxnth.page4j.model.GraphemeGroup;
import com.maxnth.page4j.model.GraphicRegion;
import com.maxnth.page4j.model.Grid;
import com.maxnth.page4j.model.GridPoints;
import com.maxnth.page4j.model.Group;
import com.maxnth.page4j.model.GroupMember;
import com.maxnth.page4j.model.ImageRegion;
import com.maxnth.page4j.model.Label;
import com.maxnth.page4j.model.LabelGroup;
import com.maxnth.page4j.model.Layer;
import com.maxnth.page4j.model.LineDrawingRegion;
import com.maxnth.page4j.model.MapRegion;
import com.maxnth.page4j.model.MathsRegion;
import com.maxnth.page4j.model.Metadata;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.MusicRegion;
import com.maxnth.page4j.model.NoiseRegion;
import com.maxnth.page4j.model.NonPrintingChar;
import com.maxnth.page4j.model.OrderedGroup;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.ReadingOrder;
import com.maxnth.page4j.model.Region;
import com.maxnth.page4j.model.RegionRef;
import com.maxnth.page4j.model.Relation;
import com.maxnth.page4j.model.SeparatorRegion;
import com.maxnth.page4j.model.TableCellRole;
import com.maxnth.page4j.model.TableRegion;
import com.maxnth.page4j.model.TextEquiv;
import com.maxnth.page4j.model.TextLine;
import com.maxnth.page4j.model.TextRegion;
import com.maxnth.page4j.model.TextStyle;
import com.maxnth.page4j.model.UnknownRegion;
import com.maxnth.page4j.model.UserAttribute;
import com.maxnth.page4j.model.Word;
import com.maxnth.page4j.model.value.Colour;
import com.maxnth.page4j.model.value.XmlValue;
import com.maxnth.page4j.pagexml.PageXmlReadResult;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.AlternativeImageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.LayerDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.LayersDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RolesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.TableCellRoleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataItemDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.UserAttributeDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.UserDefinedDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.style.TextStyleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GlyphDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemeElementDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GraphemesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class Page4jToDtoMapper {

    public PageDto toDto(PageXmlReadResult read) {
        return toDto(read.page(), read.version());
    }

    public PageDto toDto(Page page, PageXmlVersion version) {
        if (page == null) {
            return null;
        }
        return new Page4jToDtoMappingSession(page.imageWidth(), page.imageHeight()).toDto(page, version);
    }
}

final class Page4jToDtoMappingSession {

    private final int imageWidth;
    private final int imageHeight;

    Page4jToDtoMappingSession(int imageWidth, int imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    PageDto toDto(Page page, PageXmlVersion version) {
        return new PageDto(
            page.imageFilename(),
            page.imageWidth(),
            page.imageHeight(),
            page.imageXResolution(),
            page.imageYResolution(),
            xml(page.imageResolutionUnit()),
            toMetadataDto(page.metadata()),
            text(page.pcGtsId()),
            xml(page.type()),
            text(page.custom()),
            page.orientation(),
            xml(page.primaryLanguage()),
            xml(page.secondaryLanguage()),
            xml(page.primaryScript()),
            xml(page.secondaryScript()),
            xml(page.readingDirection()),
            xml(page.textLineOrder()),
            page.conf(),
            toPolygonDto(page.border()),
            toPolygonDto(page.printSpace()),
            page.regions().stream().map(this::toRegionDto).toList(),
            toReadingOrderDto(page.readingOrder()),
            toAlternativeImageDtos(page.alternativeImages()),
            toLabelsDtos(page.labels()),
            toUserDefinedDto(page.userAttributes()),
            toTextStyleDto(page.textStyle()),
            toLayersDto(page.layers()),
            toRelationsDto(page.relations()),
            version != null ? version.date() : null
        );
    }

    private MetadataDto toMetadataDto(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        String creator = text(metadata.creator());
        String created = xml(metadata.created());
        String lastChange = xml(metadata.lastChange());
        String comments = text(metadata.comments());
        String externalRef = text(metadata.externalRef());
        UserDefinedDto userDefined = toUserDefinedDto(metadata.userAttributes());
        List<MetadataItemDto> items = list(metadata.metadataItems(), item -> new MetadataItemDto(
            xml(item.type()),
            text(item.name()),
            text(item.value()),
            xml(item.date()),
            toLabelsDtos(item.labels())
        ));
        if (creator == null && created == null && lastChange == null && comments == null
            && externalRef == null && userDefined == null && items == null) {
            return null;
        }
        return new MetadataDto(creator, created, lastChange, comments, externalRef, userDefined, items);
    }

    private RegionDto toRegionDto(Region region) {
        RegionFields fields = new RegionFields();
        switch (region) {
            case TextRegion r -> {
                fields.kind = RegionKind.TextRegion;
                fields.textLines = list(r.textLines(), this::toTextLineDto);
                fields.textContentVariants = toTextContentVariantDtos(r.textEquivs());
                fields.textStyle = toTextStyleDto(r.textStyle());
                fields.type = xml(r.type());
                fields.orientation = r.orientation();
                fields.leading = r.leading();
                fields.align = xml(r.align());
                fields.readingDirection = xml(r.readingDirection());
                fields.readingOrientation = r.readingOrientation();
                fields.textLineOrder = xml(r.textLineOrder());
                fields.indented = r.indented();
                fields.primaryLanguage = xml(r.primaryLanguage());
                fields.secondaryLanguage = xml(r.secondaryLanguage());
                fields.primaryScript = xml(r.primaryScript());
                fields.secondaryScript = xml(r.secondaryScript());
                fields.production = xml(r.production());
            }
            case ImageRegion r -> {
                fields.kind = RegionKind.ImageRegion;
                fields.orientation = r.orientation();
                fields.colourDepth = xml(r.colourDepth());
                fields.backgroundColour(r.bgColour());
                fields.embText = r.embText();
            }
            case LineDrawingRegion r -> {
                fields.kind = RegionKind.LineDrawingRegion;
                fields.orientation = r.orientation();
                fields.penColour = xml(r.penColour());
                fields.backgroundColour(r.bgColour());
                fields.embText = r.embText();
            }
            case GraphicRegion r -> {
                fields.kind = RegionKind.GraphicRegion;
                fields.orientation = r.orientation();
                fields.type = xml(r.type());
                fields.numColours = r.numColours();
                fields.embText = r.embText();
            }
            case TableRegion r -> {
                fields.kind = RegionKind.TableRegion;
                fields.orientation = r.orientation();
                fields.rows = r.rows();
                fields.columns = r.columns();
                fields.lineColour = xml(r.lineColour());
                fields.backgroundColour(r.bgColour());
                fields.lineSeparators = r.lineSeparators();
                fields.embText = r.embText();
                fields.grid = toGridDto(r.grid());
            }
            case ChartRegion r -> {
                fields.kind = RegionKind.ChartRegion;
                fields.orientation = r.orientation();
                fields.type = xml(r.type());
                fields.numColours = r.numColours();
                fields.backgroundColour(r.bgColour());
                fields.embText = r.embText();
            }
            case MapRegion r -> {
                fields.kind = RegionKind.MapRegion;
                fields.orientation = r.orientation();
            }
            case SeparatorRegion r -> {
                fields.kind = RegionKind.SeparatorRegion;
                fields.orientation = r.orientation();
                fields.colour = xml(r.colour());
            }
            case MathsRegion r -> {
                fields.kind = RegionKind.MathsRegion;
                fields.orientation = r.orientation();
                fields.backgroundColour(r.bgColour());
            }
            case ChemRegion r -> {
                fields.kind = RegionKind.ChemRegion;
                fields.orientation = r.orientation();
                fields.backgroundColour(r.bgColour());
            }
            case MusicRegion r -> {
                fields.kind = RegionKind.MusicRegion;
                fields.orientation = r.orientation();
                fields.backgroundColour(r.bgColour());
            }
            case AdvertRegion r -> {
                fields.kind = RegionKind.AdvertRegion;
                fields.orientation = r.orientation();
                fields.backgroundColour(r.bgColour());
            }
            case NoiseRegion ignored -> fields.kind = RegionKind.NoiseRegion;
            case UnknownRegion ignored -> fields.kind = RegionKind.UnknownRegion;
            case CustomRegion r -> {
                fields.kind = RegionKind.CustomRegion;
                fields.type = text(r.type());
            }
            case FormRegion r -> throw unsupported(r.id(), "FormRegion");
            case FrameRegion r -> throw unsupported(r.id(), "FrameRegion");
        }

        return new RegionDto(
            region.id(),
            fields.kind,
            toPolygonDto(region.coords()),
            fields.textLines,
            fields.textContentVariants,
            toAlternativeImageDtos(region.alternativeImages()),
            toLabelsDtos(region.labels()),
            toUserDefinedDto(region.userAttributes()),
            toRolesDto(region.tableCellRole()),
            fields.grid,
            fields.textStyle,
            fields.type,
            fields.orientation,
            null,
            fields.bgColour,
            null,
            null,
            null,
            null,
            null,
            null,
            fields.leading,
            null,
            fields.align,
            null,
            null,
            fields.readingDirection,
            fields.readingOrientation,
            fields.textLineOrder,
            fields.indented,
            fields.primaryLanguage,
            fields.secondaryLanguage,
            fields.primaryScript,
            fields.secondaryScript,
            fields.production,
            fields.numColours,
            fields.embText,
            fields.colourDepth,
            fields.lineColour,
            fields.lineSeparators,
            fields.rows,
            fields.columns,
            fields.colour,
            fields.penColour,
            null,
            region.regions().isEmpty() ? null : region.regions().stream().map(this::toRegionDto).toList(),
            null,
            text(region.custom()),
            text(region.comments()),
            region.continuation()
        );
    }

    private static IllegalArgumentException unsupported(String id, String element) {
        return new IllegalArgumentException(element + " '" + id + "' is not supported by the editor yet");
    }

    private RolesDto toRolesDto(TableCellRole role) {
        if (role == null) {
            return null;
        }
        return new RolesDto(new TableCellRoleDto(role.rowIndex(), role.columnIndex(), role.rowSpan(), role.colSpan(), role.header()));
    }

    private GridDto toGridDto(Grid grid) {
        if (grid == null) {
            return null;
        }
        List<GridPointsDto> rows = new ArrayList<>();
        for (GridPoints row : grid.gridPoints()) {
            rows.add(new GridPointsDto(row.index(), toPolygonDto(row.points())));
        }
        return rows.isEmpty() ? null : new GridDto(rows);
    }

    private TextLineDto toTextLineDto(TextLine line) {
        return new TextLineDto(
            line.id(),
            toPolygonDto(line.coords()),
            toPolygonDto(line.baseline()),
            toTextContentVariantDtos(line.textEquivs()),
            list(line.words(), this::toWordDto),
            toAlternativeImageDtos(line.alternativeImages()),
            toLabelsDtos(line.labels()),
            toUserDefinedDto(line.userAttributes()),
            toTextStyleDto(line.textStyle()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            xml(line.primaryLanguage()),
            xml(line.primaryScript()),
            xml(line.secondaryScript()),
            xml(line.readingDirection()),
            xml(line.production()),
            null,
            line.index(),
            text(line.custom()),
            text(line.comments())
        );
    }

    private WordDto toWordDto(Word word) {
        return new WordDto(
            word.id(),
            toPolygonDto(word.coords()),
            toTextContentVariantDtos(word.textEquivs()),
            list(word.glyphs(), this::toGlyphDto),
            toAlternativeImageDtos(word.alternativeImages()),
            toLabelsDtos(word.labels()),
            toUserDefinedDto(word.userAttributes()),
            toTextStyleDto(word.textStyle()),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            xml(word.language()),
            xml(word.primaryScript()),
            xml(word.secondaryScript()),
            null,
            xml(word.readingDirection()),
            xml(word.production()),
            null,
            text(word.custom()),
            text(word.comments())
        );
    }

    private GlyphDto toGlyphDto(Glyph glyph) {
        List<GraphemeElementDto> graphemes = list(glyph.graphemes(), this::toGraphemeElementDto);
        return new GlyphDto(
            glyph.id(),
            toPolygonDto(glyph.coords()),
            toTextContentVariantDtos(glyph.textEquivs()),
            toAlternativeImageDtos(glyph.alternativeImages()),
            toLabelsDtos(glyph.labels()),
            toUserDefinedDto(glyph.userAttributes()),
            toTextStyleDto(glyph.textStyle()),
            graphemes != null ? new GraphemesDto(graphemes) : null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            glyph.ligature(),
            glyph.symbol(),
            xml(glyph.script()),
            xml(glyph.production()),
            null,
            text(glyph.custom()),
            text(glyph.comments())
        );
    }

    private GraphemeElementDto toGraphemeElementDto(GraphemeElement element) {
        return switch (element) {
            case Grapheme g -> new GraphemeElementDto(
                "grapheme", g.id(), g.index(), xml(g.charType()), g.ligature(), text(g.custom()), text(g.comments()),
                toPolygonDto(g.coords()), toTextContentVariantDtos(g.textEquivs()), null, null);
            case NonPrintingChar n -> new GraphemeElementDto(
                "nonPrintingChar", n.id(), n.index(), xml(n.charType()), n.ligature(), text(n.custom()), text(n.comments()),
                null, toTextContentVariantDtos(n.textEquivs()), null, null);
            case GraphemeGroup group -> new GraphemeElementDto(
                "graphemeGroup", group.id(), group.index(), xml(group.charType()), group.ligature(), text(group.custom()),
                text(group.comments()), null, toTextContentVariantDtos(group.textEquivs()), null,
                list(group.graphemes(), this::toGraphemeElementDto));
        };
    }

    private List<TextContentVariantDto> toTextContentVariantDtos(List<TextEquiv> textEquivs) {
        return list(textEquivs, equiv -> new TextContentVariantDto(
            text(equiv.unicode()),
            text(equiv.plainText()),
            equiv.conf(),
            equiv.index(),
            xml(equiv.dataType()),
            text(equiv.dataTypeDetails()),
            text(equiv.comments())
        ));
    }

    private PolygonDto toPolygonDto(Polygon polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return null;
        }
        List<PointDto> points = new ArrayList<>(polygon.size());
        for (Point point : polygon.points()) {
            points.add(new PointDto(
                CoordinateUtils.pixelToWorldX(point.x(), imageWidth),
                CoordinateUtils.pixelToWorldY(point.y(), imageHeight)
            ));
        }
        return new PolygonDto(points, polygon.confidence());
    }

    private ReadingOrderDto toReadingOrderDto(ReadingOrder readingOrder) {
        if (readingOrder == null) {
            return null;
        }
        return new ReadingOrderDto(toGroupDto(readingOrder.group(), null), readingOrder.conf());
    }

    private ReadingOrderDto.GroupDto toGroupDto(Group group, Integer index) {
        boolean ordered = group instanceof OrderedGroup;
        List<ReadingOrderDto.GroupMemberDto> members = new ArrayList<>();
        List<GroupMember> groupMembers = switch (group) {
            case OrderedGroup g -> g.members();
            case com.maxnth.page4j.model.UnorderedGroup g -> g.members();
        };
        for (int i = 0; i < groupMembers.size(); i++) {
            Integer memberIndex = ordered ? i : null;
            switch (groupMembers.get(i)) {
                case RegionRef ref -> members.add(new ReadingOrderDto.RegionRefDto(null, ref.regionRef(), memberIndex));
                case Group child -> members.add(new ReadingOrderDto.NestedGroupDto(toGroupDto(child, memberIndex)));
            }
        }
        return switch (group) {
            case OrderedGroup g -> new ReadingOrderDto.GroupDto(
                g.id(), true, index, text(g.caption()), xml(g.type()), text(g.regionRef()), members, g.continuation(),
                toUserDefinedDto(g.userAttributes()), toLabelsDtos(g.labels()), text(g.custom()), text(g.comments()));
            case com.maxnth.page4j.model.UnorderedGroup g -> new ReadingOrderDto.GroupDto(
                g.id(), false, index, text(g.caption()), xml(g.type()), text(g.regionRef()), members, g.continuation(),
                toUserDefinedDto(g.userAttributes()), toLabelsDtos(g.labels()), text(g.custom()), text(g.comments()));
        };
    }

    private LayersDto toLayersDto(List<Layer> layers) {
        List<LayerDto> dtos = list(layers, layer -> new LayerDto(
            layer.id(),
            layer.zIndex(),
            text(layer.caption()),
            layer.regionRefs().isEmpty() ? null : List.copyOf(layer.regionRefs())
        ));
        return dtos != null ? new LayersDto(dtos) : null;
    }

    private RelationsDto toRelationsDto(List<Relation> relations) {
        List<RelationDto> dtos = list(relations, relation -> new RelationDto(
            relation.id(),
            xml(relation.type()),
            relation.sourceRegionRef(),
            relation.targetRegionRef(),
            text(relation.custom()),
            text(relation.comments()),
            toLabelsDtos(relation.labels())
        ));
        return dtos != null ? new RelationsDto(dtos) : null;
    }

    private List<AlternativeImageDto> toAlternativeImageDtos(List<AlternativeImage> images) {
        return list(images, image -> new AlternativeImageDto(text(image.filename()), text(image.comments()), image.conf()));
    }

    private List<LabelsDto> toLabelsDtos(List<LabelGroup> groups) {
        return list(groups, group -> new LabelsDto(
            text(group.externalModel()),
            text(group.externalId()),
            text(group.prefix()),
            text(group.comments()),
            list(group.labels(), (Label label) -> new LabelDto(text(label.value()), text(label.type()), text(label.comments())))
        ));
    }

    private UserDefinedDto toUserDefinedDto(List<UserAttribute> attributes) {
        List<UserAttributeDto> dtos = list(attributes, attribute -> new UserAttributeDto(
            text(attribute.name()),
            text(attribute.description()),
            xml(attribute.type()),
            text(attribute.value())
        ));
        return dtos != null ? new UserDefinedDto(dtos) : null;
    }

    private TextStyleDto toTextStyleDto(TextStyle style) {
        if (style == null) {
            return null;
        }
        TextStyleDto dto = new TextStyleDto(
            text(style.fontFamily()),
            style.serif(),
            style.monospace(),
            style.fontSize(),
            style.xHeight(),
            style.kerning(),
            xml(style.textColour()),
            style.textColourRgb(),
            xml(style.bgColour()),
            style.bgColourRgb(),
            style.reverseVideo(),
            style.bold(),
            style.italic(),
            style.underlined(),
            xml(style.underlineStyle()),
            style.subscript(),
            style.superscript(),
            style.strikethrough(),
            style.smallCaps(),
            style.letterSpaced()
        );
        return dto.equals(TextStyles.EMPTY) ? null : dto;
    }

    private static <T, R> List<R> list(List<T> source, Function<T, R> mapper) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return source.stream().map(mapper).toList();
    }

    private static String xml(XmlValue value) {
        return value != null ? text(value.xmlValue()) : null;
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static final class RegionFields {
        RegionKind kind;
        List<TextLineDto> textLines;
        List<TextContentVariantDto> textContentVariants;
        GridDto grid;
        TextStyleDto textStyle;
        String type;
        Double orientation;
        String bgColour;
        Integer leading;
        String align;
        String readingDirection;
        Double readingOrientation;
        String textLineOrder;
        Boolean indented;
        String primaryLanguage;
        String secondaryLanguage;
        String primaryScript;
        String secondaryScript;
        String production;
        Integer numColours;
        Boolean embText;
        String colourDepth;
        String lineColour;
        Boolean lineSeparators;
        Integer rows;
        Integer columns;
        String colour;
        String penColour;

        void backgroundColour(Colour colour) {
            bgColour = xml(colour);
            if (bgColour != null) {
                textStyle = TextStyles.withBackground(bgColour);
            }
        }
    }
}
