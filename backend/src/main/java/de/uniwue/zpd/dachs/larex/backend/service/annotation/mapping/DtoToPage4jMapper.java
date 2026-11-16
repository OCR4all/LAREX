package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

import com.maxnth.page4j.geometry.Point;
import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.AdvertRegionBuilder;
import com.maxnth.page4j.model.AlternativeImage;
import com.maxnth.page4j.model.ChartRegionBuilder;
import com.maxnth.page4j.model.ChemRegionBuilder;
import com.maxnth.page4j.model.CustomRegionBuilder;
import com.maxnth.page4j.model.FormRegionBuilder;
import com.maxnth.page4j.model.GlyphBuilder;
import com.maxnth.page4j.model.Glyph;
import com.maxnth.page4j.model.Grapheme;
import com.maxnth.page4j.model.GraphemeElement;
import com.maxnth.page4j.model.GraphemeGroup;
import com.maxnth.page4j.model.GraphicRegionBuilder;
import com.maxnth.page4j.model.Grid;
import com.maxnth.page4j.model.GridAddPoints;
import com.maxnth.page4j.model.GridPoints;
import com.maxnth.page4j.model.Group;
import com.maxnth.page4j.model.GroupMember;
import com.maxnth.page4j.model.ImageRegionBuilder;
import com.maxnth.page4j.model.InvalidPageException;
import com.maxnth.page4j.model.Label;
import com.maxnth.page4j.model.LabelGroup;
import com.maxnth.page4j.model.Layer;
import com.maxnth.page4j.model.LineDrawingRegionBuilder;
import com.maxnth.page4j.model.MapRegionBuilder;
import com.maxnth.page4j.model.MathsRegionBuilder;
import com.maxnth.page4j.model.Metadata;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.MusicRegionBuilder;
import com.maxnth.page4j.model.NoiseRegionBuilder;
import com.maxnth.page4j.model.NonPrintingChar;
import com.maxnth.page4j.model.OrderedGroup;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.ReadingOrder;
import com.maxnth.page4j.model.Region;
import com.maxnth.page4j.model.RegionRef;
import com.maxnth.page4j.model.Relation;
import com.maxnth.page4j.model.SeparatorRegionBuilder;
import com.maxnth.page4j.model.TableCellRole;
import com.maxnth.page4j.model.TableRegionBuilder;
import com.maxnth.page4j.model.TextEquiv;
import com.maxnth.page4j.model.TextLine;
import com.maxnth.page4j.model.TextLineBuilder;
import com.maxnth.page4j.model.TextRegionBuilder;
import com.maxnth.page4j.model.TextStyle;
import com.maxnth.page4j.model.UnknownRegionBuilder;
import com.maxnth.page4j.model.UnorderedGroup;
import com.maxnth.page4j.model.UserAttribute;
import com.maxnth.page4j.model.Word;
import com.maxnth.page4j.model.WordBuilder;
import com.maxnth.page4j.model.value.Align;
import com.maxnth.page4j.model.value.CharType;
import com.maxnth.page4j.model.value.ChartType;
import com.maxnth.page4j.model.value.Colour;
import com.maxnth.page4j.model.value.ColourDepth;
import com.maxnth.page4j.model.value.GraphicsType;
import com.maxnth.page4j.model.value.GroupType;
import com.maxnth.page4j.model.value.Language;
import com.maxnth.page4j.model.value.MetadataItemType;
import com.maxnth.page4j.model.value.Mirrored;
import com.maxnth.page4j.model.value.PageDateTime;
import com.maxnth.page4j.model.value.PageType;
import com.maxnth.page4j.model.value.Production;
import com.maxnth.page4j.model.value.ReadingDirection;
import com.maxnth.page4j.model.value.RelationType;
import com.maxnth.page4j.model.value.ResolutionUnit;
import com.maxnth.page4j.model.value.Script;
import com.maxnth.page4j.model.value.TextDataType;
import com.maxnth.page4j.model.value.TextLineOrder;
import com.maxnth.page4j.model.value.TextType;
import com.maxnth.page4j.model.value.UnderlineStyle;
import com.maxnth.page4j.model.value.UserAttributeType;
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
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class DtoToPage4jMapper {

    public Page toPage4j(PageDto dto) {
        if (dto == null) {
            return null;
        }
        return new DtoToPage4jMappingSession(dto).toPage4j();
    }
}

final class DtoToPage4jMappingSession {

    private static final Logger log = LoggerFactory.getLogger(DtoToPage4jMapper.class);

    private final PageDto dto;
    private final int imageWidth;
    private final int imageHeight;
    private final Map<Object, String> ids = new IdentityHashMap<>();
    private final Set<String> knownIds = new HashSet<>();
    private final Set<String> claimedIds = new HashSet<>();
    private final Map<String, String> repairedIds = new HashMap<>();
    private final Map<String, Integer> idCounters = new HashMap<>();
    private String pcGtsId;

    DtoToPage4jMappingSession(PageDto dto) {
        this.dto = dto;
        this.imageWidth = dto.imageWidth();
        this.imageHeight = dto.imageHeight();
    }

    Page toPage4j() {
        assignIds();

        Page.Builder page = Page.builder()
            .pcGtsId(pcGtsId)
            .metadata(toMetadata(dto.metadata()))
            .imageFilename(dto.imageFilename() != null ? dto.imageFilename() : "")
            .imageWidth(dto.imageWidth())
            .imageHeight(dto.imageHeight())
            .imageXResolution(dto.imageXResolution())
            .imageYResolution(dto.imageYResolution())
            .imageResolutionUnit(value(dto.imageResolutionUnit(), ResolutionUnit::fromXml, "Page imageResolutionUnit"))
            .type(value(dto.type(), PageType::fromXml, "Page type"))
            .custom(dto.custom())
            .comments(dto.comments())
            .orientation(dto.orientation())
            .primaryLanguage(language(dto.primaryLanguage(), "Page primaryLanguage"))
            .secondaryLanguage(language(dto.secondaryLanguage(), "Page secondaryLanguage"))
            .customLanguages(dto.customLanguages())
            .primaryScript(value(dto.primaryScript(), Script::fromXml, "Page primaryScript"))
            .secondaryScript(value(dto.secondaryScript(), Script::fromXml, "Page secondaryScript"))
            .customScripts(dto.customScripts())
            .readingDirection(value(dto.readingDirection(), ReadingDirection::fromXml, "Page readingDirection"))
            .textLineOrder(value(dto.textLineOrder(), TextLineOrder::fromXml, "Page textLineOrder"))
            .conf(dto.confidence())
            .alternativeImages(toAlternativeImages(dto.alternativeImages()))
            .border(toPolygon(dto.border()))
            .printSpace(toPolygon(dto.printSpace()))
            .textStyle(toTextStyle(dto.textStyle(), "Page"))
            .userAttributes(toUserAttributes(dto.userDefined()))
            .labels(toLabelGroups(dto.labels()))
            .regions(each(dto.regions(), this::toRegion))
            .readingOrder(toReadingOrder(dto.readingOrder()))
            .layers(toLayers(dto.layers()))
            .relations(toRelations(dto.relations()));

        try {
            return page.build();
        } catch (InvalidPageException e) {
            throw new PageDtoMappingException("Invalid page: " + e.getMessage(), e);
        }
    }

    private void assignIds() {
        reserve(dto.pcGtsId());
        forEach(dto.regions(), this::reserveRegion);
        if (dto.readingOrder() != null) {
            reserveGroup(dto.readingOrder().root());
        }
        if (dto.layers() != null) {
            forEach(dto.layers().layers(), layer -> reserve(layer.id()));
        }
        if (dto.relations() != null) {
            forEach(dto.relations().relations(), relation -> reserve(relation.id()));
        }

        if (isValidId(dto.pcGtsId())) {
            pcGtsId = dto.pcGtsId();
            claimedIds.add(pcGtsId);
        } else if (dto.pcGtsId() != null) {
            log.warn("Dropping invalid pcGtsId '{}' on save", dto.pcGtsId());
        }
        forEach(dto.regions(), this::claimRegion);
        if (dto.readingOrder() != null) {
            claimGroup(dto.readingOrder().root());
        }
        if (dto.layers() != null) {
            forEach(dto.layers().layers(), layer -> claim(layer, layer.id(), "layer"));
        }
        if (dto.relations() != null) {
            forEach(dto.relations().relations(), relation -> {
                if (relation.id() != null) {
                    claim(relation, relation.id(), "rel");
                }
            });
        }
        knownIds.addAll(claimedIds);
    }

    private void reserve(String id) {
        if (isValidId(id)) {
            knownIds.add(id);
        }
    }

    private void reserveRegion(RegionDto region) {
        reserve(region.id());
        forEach(region.textLines(), line -> {
            reserve(line.id());
            forEach(line.words(), word -> {
                reserve(word.id());
                forEach(word.glyphs(), glyph -> {
                    reserve(glyph.id());
                    if (glyph.graphemes() != null) {
                        forEach(glyph.graphemes().elements(), this::reserveGrapheme);
                    }
                });
            });
        });
        forEach(region.nestedRegions(), this::reserveRegion);
    }

    private void reserveGrapheme(GraphemeElementDto element) {
        reserve(element.id());
        forEach(element.members(), this::reserveGrapheme);
    }

    private void reserveGroup(ReadingOrderDto.GroupDto group) {
        if (group == null) {
            return;
        }
        reserve(group.id());
        forEach(group.members(), member -> {
            if (member instanceof ReadingOrderDto.NestedGroupDto nested) {
                reserveGroup(nested.group());
            }
        });
    }

    private void claimRegion(RegionDto region) {
        claim(region, region.id(), "r");
        forEach(region.textLines(), line -> {
            claim(line, line.id(), "l");
            forEach(line.words(), word -> {
                claim(word, word.id(), "w");
                forEach(word.glyphs(), glyph -> {
                    claim(glyph, glyph.id(), "g");
                    if (glyph.graphemes() != null) {
                        forEach(glyph.graphemes().elements(), this::claimGrapheme);
                    }
                });
            });
        });
        forEach(region.nestedRegions(), this::claimRegion);
    }

    private void claimGrapheme(GraphemeElementDto element) {
        claim(element, element.id(), "gr");
        forEach(element.members(), this::claimGrapheme);
    }

    private void claimGroup(ReadingOrderDto.GroupDto group) {
        if (group == null) {
            return;
        }
        claim(group, group.id(), "ro");
        forEach(group.members(), member -> {
            if (member instanceof ReadingOrderDto.NestedGroupDto nested) {
                claimGroup(nested.group());
            }
        });
    }

    private void claim(Object owner, String id, String prefix) {
        if (isValidId(id) && claimedIds.add(id)) {
            ids.put(owner, id);
            return;
        }
        String generated;
        do {
            generated = prefix + idCounters.merge(prefix, 1, Integer::sum);
        } while (knownIds.contains(generated) || claimedIds.contains(generated));
        claimedIds.add(generated);
        ids.put(owner, generated);
        if (id != null && !isValidId(id)) {
            repairedIds.putIfAbsent(id, generated);
        }
        log.warn("Repaired {} ID '{}' on save as '{}'", id == null || id.isBlank() ? "missing" : isValidId(id) ? "duplicate" : "invalid", id, generated);
    }

    private String idOf(Object owner) {
        return ids.get(owner);
    }

    private String resolve(String ref) {
        if (!hasText(ref)) {
            return null;
        }
        return knownIds.contains(ref) ? ref : repairedIds.get(ref);
    }

    private static boolean isValidId(String id) {
        if (id == null || id.isEmpty() || !isNameStartChar(id.codePointAt(0))) {
            return false;
        }
        return id.codePoints().allMatch(c -> isNameStartChar(c) || isNameChar(c));
    }

    private static boolean isNameStartChar(int c) {
        return c >= 'A' && c <= 'Z' || c == '_' || c >= 'a' && c <= 'z'
            || c >= 0xC0 && c <= 0xD6 || c >= 0xD8 && c <= 0xF6 || c >= 0xF8 && c <= 0x2FF
            || c >= 0x370 && c <= 0x37D || c >= 0x37F && c <= 0x1FFF || c >= 0x200C && c <= 0x200D
            || c >= 0x2070 && c <= 0x218F || c >= 0x2C00 && c <= 0x2FEF || c >= 0x3001 && c <= 0xD7FF
            || c >= 0xF900 && c <= 0xFDCF || c >= 0xFDF0 && c <= 0xFFFD || c >= 0x10000 && c <= 0xEFFFF;
    }

    private static boolean isNameChar(int c) {
        return c == '-' || c == '.' || c >= '0' && c <= '9' || c == 0xB7
            || c >= 0x300 && c <= 0x36F || c >= 0x203F && c <= 0x2040;
    }

    private Metadata toMetadata(MetadataDto metadata) {
        String creator = metadata != null ? metadata.creator() : null;
        return new Metadata(
            metadata != null ? metadata.externalRef() : null,
            creator != null ? creator : "",
            timestamp(metadata != null ? metadata.created() : null, "created"),
            timestamp(metadata != null ? metadata.lastChange() : null, "lastChange"),
            metadata != null ? metadata.comments() : null,
            metadata != null ? toUserAttributes(metadata.userDefined()) : List.of(),
            metadata != null ? each(metadata.items(), this::toMetadataItem) : List.of()
        );
    }

    private static PageDateTime timestamp(String value, String field) {
        String normalized = normalize(value);
        if (normalized != null) {
            try {
                return PageDateTime.parse(normalized);
            } catch (IllegalArgumentException e) {
                log.warn("Replacing unparseable Metadata {} '{}' with the current time", field, normalized);
            }
        }
        return PageDateTime.now();
    }

    private MetadataItem toMetadataItem(MetadataItemDto item) {
        String date = normalize(item.date());
        String value = item.value();
        return new MetadataItem(
            value(item.type(), MetadataItemType::fromXml, "MetadataItem type"),
            item.name(),
            value != null ? value : "",
            date != null ? value(date, PageDateTime::parse, "MetadataItem date") : null,
            toLabelGroups(item.labels())
        );
    }

    private Region toRegion(RegionDto region) {
        String id = idOf(region);
        String context = (region.kind() != null ? region.kind().name() : "Region") + " '" + id + "'";
        Polygon coords = requiredPolygon(region.coords(), context);
        String custom = region.custom();
        String comments = region.comments();
        List<AlternativeImage> alternativeImages = toAlternativeImages(region.alternativeImages());
        List<UserAttribute> userAttributes = toUserAttributes(region.userDefined());
        List<LabelGroup> labels = toLabelGroups(region.labels());
        TableCellRole role = toTableCellRole(region.tableCellRoleFromRoles(), context);
        List<Region> regions = each(region.nestedRegions(), this::toRegion);
        RegionKind kind = region.kind() != null ? region.kind() : RegionKind.UnknownRegion;
        Mirrored mirrored = value(region.mirrored(), Mirrored::fromXml, context + " mirrored");

        return switch (kind) {
            case TextRegion -> TextRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .type(textType(region.type(), context))
                .leading(region.leading())
                .readingDirection(value(region.readingDirection(), ReadingDirection::fromXml, context + " readingDirection"))
                .textLineOrder(value(region.textLineOrder(), TextLineOrder::fromXml, context + " textLineOrder"))
                .readingOrientation(region.readingOrientation())
                .indented(region.indented())
                .align(value(region.align(), Align::fromXml, context + " align"))
                .primaryLanguage(language(region.primaryLanguage(), context + " primaryLanguage"))
                .secondaryLanguage(language(region.secondaryLanguage(), context + " secondaryLanguage"))
                .customLanguages(region.customLanguages())
                .primaryScript(value(region.primaryScript(), Script::fromXml, context + " primaryScript"))
                .secondaryScript(value(region.secondaryScript(), Script::fromXml, context + " secondaryScript"))
                .customScripts(region.customScripts())
                .production(value(region.production(), Production::fromXml, context + " production"))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .textLines(each(region.textLines(), this::toTextLine))
                .textEquivs(toTextEquivs(region.textContentVariants(), context))
                .textStyle(toTextStyle(region.textStyle() != null ? region.textStyle() : flatRegionStyle(region), context))
                .build();
            case ImageRegion -> ImageRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .colourDepth(value(region.colourDepth(), ColourDepth::fromXml, context + " colourDepth"))
                .bgColour(regionBackground(region, context))
                .embText(region.embText())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case LineDrawingRegion -> LineDrawingRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .penColour(value(region.penColour(), Colour::fromXml, context + " penColour"))
                .bgColour(regionBackground(region, context))
                .embText(region.embText())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case GraphicRegion -> GraphicRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .type(value(region.type(), GraphicsType::fromXml, context + " type"))
                .numColours(region.numColours())
                .embText(region.embText())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case TableRegion -> TableRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .rows(region.rows())
                .columns(region.columns())
                .lineColour(value(region.lineColour(), Colour::fromXml, context + " lineColour"))
                .bgColour(regionBackground(region, context))
                .lineSeparators(region.lineSeparators())
                .embText(region.embText())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .grid(toGrid(region.grid(), context))
                .build();
            case ChartRegion -> ChartRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .type(value(region.type(), ChartType::fromXml, context + " type"))
                .numColours(region.numColours())
                .bgColour(regionBackground(region, context))
                .embText(region.embText())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case MapRegion -> MapRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case SeparatorRegion -> SeparatorRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .colour(value(region.colour(), Colour::fromXml, context + " colour"))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case MathsRegion -> MathsRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .bgColour(regionBackground(region, context))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case ChemRegion -> ChemRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .bgColour(regionBackground(region, context))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case MusicRegion -> MusicRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .bgColour(regionBackground(region, context))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case AdvertRegion -> AdvertRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .bgColour(regionBackground(region, context))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case NoiseRegion -> NoiseRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case UnknownRegion -> UnknownRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case FormRegion -> FormRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .orientation(region.orientation())
                .mirrored(mirrored)
                .bgColour(regionBackground(region, context))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
            case CustomRegion -> CustomRegionBuilder.builder()
                .id(id).custom(custom).comments(comments).continuation(region.continuation())
                .type(normalize(region.type()))
                .alternativeImages(alternativeImages).coords(coords).userAttributes(userAttributes).labels(labels)
                .tableCellRole(role).regions(regions)
                .build();
        };
    }

    private TextType textType(String type, String context) {
        String normalized = normalize(type);
        if (normalized != null && "custom".equalsIgnoreCase(normalized)) {
            normalized = "other";
        }
        return value(normalized, TextType::fromXml, context + " type");
    }

    private Colour regionBackground(RegionDto region, String context) {
        String bgColour = region.textStyle() != null ? region.textStyle().bgColour() : region.bgColour();
        return value(bgColour, Colour::fromXml, context + " bgColour");
    }

    private static TextStyleDto flatRegionStyle(RegionDto region) {
        return new TextStyleDto(
            region.fontFamily(),
            region.serif(),
            region.monospace(),
            region.fontSize(),
            region.xHeight(),
            region.kerning(),
            region.textColour(),
            region.textColourRgb(),
            region.bgColour(),
            region.bgColourRgb(),
            region.reverseVideo(),
            null, null, null, null, null, null, null, null, null
        );
    }

    private TableCellRole toTableCellRole(TableCellRoleDto role, String context) {
        if (role == null) {
            return null;
        }
        if (role.rowIndex() == null || role.columnIndex() == null) {
            throw new PageDtoMappingException(context + " TableCellRole needs rowIndex and columnIndex");
        }
        return new TableCellRole(role.rowIndex(), role.columnIndex(), role.rowSpan(), role.colSpan(), role.header());
    }

    private Grid toGrid(GridDto grid, String context) {
        if (grid == null) {
            return null;
        }
        List<GridAddPoints> addPoints = new ArrayList<>();
        forEach(grid.addPoints(), points -> {
            if (points.row() == null || points.col() == null) {
                throw new PageDtoMappingException(context + " AddPoints needs row and col");
            }
            addPoints.add(new GridAddPoints(points.row(), points.col(), points.left(), points.top()));
        });
        if (grid.rows() == null || grid.rows().isEmpty()) {
            return addPoints.isEmpty() ? null : new Grid(List.of(), addPoints);
        }
        List<GridPointsDto> rows = grid.rows().stream()
            .filter(row -> row != null && toPolygon(row.points()) != null)
            .sorted(Comparator.comparing(row -> row.index() != null ? row.index() : Integer.MAX_VALUE))
            .toList();
        if (rows.isEmpty()) {
            return addPoints.isEmpty() ? null : new Grid(List.of(), addPoints);
        }
        boolean keepIndexes = rows.stream().allMatch(row -> row.index() != null)
            && rows.stream().map(GridPointsDto::index).distinct().count() == rows.size();
        List<GridPoints> points = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            GridPointsDto row = rows.get(i);
            points.add(new GridPoints(keepIndexes ? row.index() : i, toPolygon(row.points())));
        }
        return new Grid(points, addPoints);
    }

    private TextLine toTextLine(TextLineDto line) {
        String id = idOf(line);
        String context = "TextLine '" + id + "'";
        return TextLineBuilder.builder()
            .id(id)
            .orientation(line.orientation())
            .mirrored(value(line.mirrored(), Mirrored::fromXml, context + " mirrored"))
            .primaryLanguage(language(line.primaryLanguage(), context + " primaryLanguage"))
            .secondaryLanguage(language(line.secondaryLanguage(), context + " secondaryLanguage"))
            .customLanguages(line.customLanguages())
            .primaryScript(value(line.primaryScript(), Script::fromXml, context + " primaryScript"))
            .secondaryScript(value(line.secondaryScript(), Script::fromXml, context + " secondaryScript"))
            .customScripts(line.customScripts())
            .readingDirection(value(line.readingDirection(), ReadingDirection::fromXml, context + " readingDirection"))
            .production(value(line.production(), Production::fromXml, context + " production"))
            .custom(line.custom())
            .comments(line.comments())
            .index(line.index())
            .alternativeImages(toAlternativeImages(line.alternativeImages()))
            .coords(requiredPolygon(line.coords(), context))
            .ascentLine(toPolygon(line.ascentLine()))
            .meanLine(toPolygon(line.meanLine()))
            .baseline(toPolygon(line.baseline()))
            .descentLine(toPolygon(line.descentLine()))
            .words(each(line.words(), this::toWord))
            .textEquivs(toTextEquivs(line.textContentVariants(), context))
            .textStyle(toTextStyle(withFlags(line.textStyle(), line.bold(), line.italic(), line.underlined(),
                line.underlineStyle(), line.subscript(), line.superscript(), line.strikethrough(), line.smallCaps(),
                line.letterSpaced()), context))
            .userAttributes(toUserAttributes(line.userDefined()))
            .labels(toLabelGroups(line.labels()))
            .build();
    }

    private Word toWord(WordDto word) {
        String id = idOf(word);
        String context = "Word '" + id + "'";
        return WordBuilder.builder()
            .id(id)
            .orientation(word.orientation())
            .mirrored(value(word.mirrored(), Mirrored::fromXml, context + " mirrored"))
            .language(language(word.language(), context + " language"))
            .customLanguages(word.customLanguages())
            .primaryScript(value(word.primaryScript(), Script::fromXml, context + " primaryScript"))
            .secondaryScript(value(word.secondaryScript(), Script::fromXml, context + " secondaryScript"))
            .customScripts(word.customScripts())
            .readingDirection(value(word.readingDirection(), ReadingDirection::fromXml, context + " readingDirection"))
            .production(value(word.production(), Production::fromXml, context + " production"))
            .custom(word.custom())
            .comments(word.comments())
            .alternativeImages(toAlternativeImages(word.alternativeImages()))
            .coords(requiredPolygon(word.coords(), context))
            .glyphs(each(word.glyphs(), this::toGlyph))
            .textEquivs(toTextEquivs(word.textContentVariants(), context))
            .textStyle(toTextStyle(withFlags(word.textStyle(), word.bold(), word.italic(), word.underlined(),
                word.underlineStyle(), word.subscript(), word.superscript(), word.strikethrough(), word.smallCaps(),
                word.letterSpaced()), context))
            .userAttributes(toUserAttributes(word.userDefined()))
            .labels(toLabelGroups(word.labels()))
            .build();
    }

    private Glyph toGlyph(GlyphDto glyph) {
        String id = idOf(glyph);
        String context = "Glyph '" + id + "'";
        return GlyphBuilder.builder()
            .id(id)
            .orientation(glyph.orientation())
            .mirrored(value(glyph.mirrored(), Mirrored::fromXml, context + " mirrored"))
            .ligature(glyph.ligature())
            .symbol(glyph.symbol())
            .script(value(glyph.script(), Script::fromXml, context + " script"))
            .customScript(glyph.customScript())
            .production(value(glyph.production(), Production::fromXml, context + " production"))
            .custom(glyph.custom())
            .comments(glyph.comments())
            .alternativeImages(toAlternativeImages(glyph.alternativeImages()))
            .coords(requiredPolygon(glyph.coords(), context))
            .graphemes(glyph.graphemes() != null ? toGraphemeElements(glyph.graphemes().elements(), false) : List.of())
            .textEquivs(toTextEquivs(glyph.textContentVariants(), context))
            .textStyle(toTextStyle(withFlags(glyph.textStyle(), glyph.bold(), glyph.italic(), glyph.underlined(),
                glyph.underlineStyle(), glyph.subscript(), glyph.superscript(), glyph.strikethrough(), glyph.smallCaps(),
                glyph.letterSpaced()), context))
            .userAttributes(toUserAttributes(glyph.userDefined()))
            .labels(toLabelGroups(glyph.labels()))
            .build();
    }

    private List<GraphemeElement> toGraphemeElements(List<GraphemeElementDto> elements, boolean inGroup) {
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        List<GraphemeElementDto> sorted = elements.stream()
            .filter(element -> element != null)
            .sorted(Comparator.comparing(element -> element.index() != null ? element.index() : Integer.MAX_VALUE))
            .toList();
        List<GraphemeElement> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            result.add(toGraphemeElement(sorted.get(i), i, inGroup));
        }
        return result;
    }

    private GraphemeElement toGraphemeElement(GraphemeElementDto element, int position, boolean inGroup) {
        String id = idOf(element);
        String kind = element.kind() != null ? element.kind() : "grapheme";
        String context = kind + " '" + id + "'";
        int index = element.index() != null ? element.index() : position;
        CharType charType = value(element.charType(), CharType::fromXml, context + " charType");
        String custom = element.custom();
        String comments = element.comments();
        List<TextEquiv> textEquivs = toTextEquivs(element.textContentVariants(), context);
        return switch (kind) {
            case "graphemeGroup" -> {
                if (inGroup) {
                    throw new PageDtoMappingException(context + " is nested in another grapheme group");
                }
                yield new GraphemeGroup(id, index, element.ligature(), charType, custom, comments, textEquivs,
                    toGraphemeElements(element.members(), true));
            }
            case "nonPrintingChar" -> new NonPrintingChar(id, index, element.ligature(), charType, custom, comments, textEquivs);
            default -> new Grapheme(id, index, element.ligature(), charType, custom, comments, textEquivs,
                requiredPolygon(element.coords(), context));
        };
    }

    private TextStyleDto withFlags(TextStyleDto style, Boolean bold, Boolean italic, Boolean underlined,
                                   String underlineStyle, Boolean subscript, Boolean superscript,
                                   Boolean strikethrough, Boolean smallCaps, Boolean letterSpaced) {
        TextStyleDto base = style != null ? style : TextStyles.EMPTY;
        return new TextStyleDto(
            base.fontFamily(),
            base.serif(),
            base.monospace(),
            base.fontSize(),
            base.xHeight(),
            base.kerning(),
            base.textColour(),
            base.textColourRgb(),
            base.bgColour(),
            base.bgColourRgb(),
            base.reverseVideo(),
            firstNonNull(bold, base.bold()),
            firstNonNull(italic, base.italic()),
            firstNonNull(underlined, base.underlined()),
            firstNonBlank(underlineStyle, base.underlineStyle()),
            firstNonNull(subscript, base.subscript()),
            firstNonNull(superscript, base.superscript()),
            firstNonNull(strikethrough, base.strikethrough()),
            firstNonNull(smallCaps, base.smallCaps()),
            firstNonNull(letterSpaced, base.letterSpaced())
        );
    }

    private TextStyle toTextStyle(TextStyleDto style, String context) {
        if (style == null) {
            return null;
        }
        TextStyle textStyle = new TextStyle(
            style.fontFamily(),
            style.serif(),
            style.monospace(),
            style.fontSize(),
            style.xHeight(),
            style.kerning(),
            value(style.textColour(), Colour::fromXml, context + " TextStyle textColour"),
            style.textColourRgb(),
            value(style.bgColour(), Colour::fromXml, context + " TextStyle bgColour"),
            style.bgColourRgb(),
            style.reverseVideo(),
            style.bold(),
            style.italic(),
            style.underlined(),
            value(style.underlineStyle(), UnderlineStyle::fromXml, context + " TextStyle underlineStyle"),
            style.subscript(),
            style.superscript(),
            style.strikethrough(),
            style.smallCaps(),
            style.letterSpaced()
        );
        return textStyle.equals(EMPTY_TEXT_STYLE) ? null : textStyle;
    }

    private static final TextStyle EMPTY_TEXT_STYLE = new TextStyle(
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null
    );

    private List<TextEquiv> toTextEquivs(List<TextContentVariantDto> variants, String context) {
        return each(variants, variant -> new TextEquiv(
            variant.index(),
            variant.confidence(),
            hasText(variant.dataType()) ? value(variant.dataType(), TextDataType::fromXml, context + " TextEquiv dataType") : null,
            variant.dataTypeDetails(),
            variant.comments(),
            variant.plainText(),
            variant.unicode() != null ? variant.unicode() : ""
        ));
    }

    private ReadingOrder toReadingOrder(ReadingOrderDto readingOrder) {
        if (readingOrder == null || readingOrder.root() == null) {
            return null;
        }
        Group root = toGroup(readingOrder.root());
        return root != null ? new ReadingOrder(readingOrder.confidence(), root) : null;
    }

    private Group toGroup(ReadingOrderDto.GroupDto group) {
        String id = idOf(group);
        String context = "Group '" + id + "'";
        List<GroupMember> members = new ArrayList<>();
        forEach(group.members(), member -> {
            switch (member) {
                case ReadingOrderDto.RegionRefDto ref -> {
                    if (!hasText(ref.regionRef())) {
                        return;
                    }
                    String target = resolve(ref.regionRef());
                    if (target == null) {
                        log.warn("Dropping reading order reference to missing element '{}' in {}", ref.regionRef(), context);
                        return;
                    }
                    members.add(new RegionRef(target));
                }
                case ReadingOrderDto.NestedGroupDto nested -> {
                    Group child = nested.group() != null ? toGroup(nested.group()) : null;
                    if (child != null) {
                        members.add(child);
                    }
                }
            }
        });
        if (members.isEmpty()) {
            log.warn("Dropping {} on save because it has no members", context);
            knownIds.remove(id);
            return null;
        }
        String regionRef = resolve(group.regionRef());
        if (hasText(group.regionRef()) && regionRef == null) {
            log.warn("Dropping regionRef to missing element '{}' of {}", group.regionRef(), context);
        }
        String caption = group.caption();
        GroupType type = value(group.groupType(), GroupType::fromXml, context + " type");
        String custom = group.custom();
        String comments = group.comments();
        List<UserAttribute> userAttributes = toUserAttributes(group.userDefined());
        List<LabelGroup> labels = toLabelGroups(group.labels());
        return group.ordered()
            ? new OrderedGroup(id, regionRef, caption, type, group.continuation(), custom, comments, userAttributes, labels, members)
            : new UnorderedGroup(id, regionRef, caption, type, group.continuation(), custom, comments, userAttributes, labels, members);
    }

    private List<Layer> toLayers(LayersDto layers) {
        if (layers == null || layers.layers() == null) {
            return List.of();
        }
        List<Layer> result = new ArrayList<>();
        for (int i = 0; i < layers.layers().size(); i++) {
            LayerDto layer = layers.layers().get(i);
            if (layer == null) {
                continue;
            }
            String id = idOf(layer);
            List<String> regionRefs = new ArrayList<>();
            forEach(layer.regionRefs(), ref -> {
                if (!hasText(ref)) {
                    return;
                }
                String target = resolve(ref);
                if (target == null) {
                    log.warn("Dropping layer reference to missing element '{}' in Layer '{}'", ref, id);
                    return;
                }
                regionRefs.add(target);
            });
            if (regionRefs.isEmpty()) {
                log.warn("Dropping Layer '{}' on save because it references no existing element", id);
                continue;
            }
            result.add(new Layer(id, layer.zIndex() != null ? layer.zIndex() : i, layer.caption(), regionRefs));
        }
        return result;
    }

    private List<Relation> toRelations(RelationsDto relations) {
        if (relations == null || relations.relations() == null) {
            return List.of();
        }
        List<Relation> result = new ArrayList<>();
        for (RelationDto relation : relations.relations()) {
            if (relation == null) {
                continue;
            }
            String source = resolve(relation.sourceRegionRef());
            String target = resolve(relation.targetRegionRef());
            if (source == null || target == null) {
                log.warn("Dropping relation '{}' between missing elements '{}' and '{}'",
                    relation.id(), relation.sourceRegionRef(), relation.targetRegionRef());
                continue;
            }
            result.add(new Relation(
                idOf(relation),
                relationType(relation.type()),
                relation.custom(),
                relation.comments(),
                toLabelGroups(relation.labels()),
                source,
                target
            ));
        }
        return result;
    }

    private List<AlternativeImage> toAlternativeImages(List<AlternativeImageDto> images) {
        List<AlternativeImage> result = new ArrayList<>();
        forEach(images, image -> {
            if (hasText(image.filename())) {
                result.add(new AlternativeImage(image.filename(), image.comments(), image.confidence()));
            }
        });
        return result;
    }

    private List<LabelGroup> toLabelGroups(List<LabelsDto> groups) {
        return each(groups, group -> {
            List<Label> labels = new ArrayList<>();
            forEach(group.labels(), (LabelDto label) -> {
                if (label.value() != null) {
                    labels.add(new Label(label.value(), label.type(), label.comments()));
                }
            });
            return new LabelGroup(
                group.externalModel(),
                group.externalId(),
                group.prefix(),
                group.comments(),
                labels
            );
        });
    }

    private List<UserAttribute> toUserAttributes(UserDefinedDto userDefined) {
        List<UserAttribute> result = new ArrayList<>();
        if (userDefined == null) {
            return result;
        }
        forEach(userDefined.attributes(), (UserAttributeDto attribute) -> {
            if (!hasText(attribute.name())) {
                return;
            }
            result.add(new UserAttribute(
                attribute.name(),
                attribute.description(),
                userAttributeType(attribute.type()),
                attribute.value()
            ));
        });
        return result;
    }

    private static RelationType relationType(String type) {
        String normalized = normalize(type);
        if (normalized == null) {
            return null;
        }
        return "join".equalsIgnoreCase(normalized) ? RelationType.JOIN : RelationType.LINK;
    }

    private static UserAttributeType userAttributeType(String type) {
        String normalized = normalize(type);
        if ("xsd:integer".equals(normalized)) {
            return UserAttributeType.XSD_INTEGER;
        }
        if ("xsd:float".equals(normalized)) {
            return UserAttributeType.XSD_FLOAT;
        }
        if ("xsd:boolean".equals(normalized)) {
            return UserAttributeType.XSD_BOOLEAN;
        }
        return UserAttributeType.XSD_STRING;
    }

    private Polygon requiredPolygon(PolygonDto polygon, String context) {
        Polygon result = toPolygon(polygon);
        if (result == null) {
            throw new PageDtoMappingException(context + " has no coordinates");
        }
        return result;
    }

    private Polygon toPolygon(PolygonDto polygon) {
        if (polygon == null || polygon.points() == null || polygon.points().isEmpty()) {
            return null;
        }
        List<Point> points = new ArrayList<>(polygon.points().size());
        for (PointDto point : polygon.points()) {
            points.add(new Point(
                Math.max(0, CoordinateUtils.worldToPixelX(point.x(), imageWidth)),
                Math.max(0, CoordinateUtils.worldToPixelY(point.y(), imageHeight))
            ));
        }
        return new Polygon(points, polygon.confidence());
    }

    private static Language language(String value, String context) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        Language language = pageLanguage(normalized);
        if (language == null) {
            language = isoLanguage(normalized);
        }
        if (language == null) {
            throw new PageDtoMappingException(context + ": '" + normalized + "' is not a PAGE XML language");
        }
        return language;
    }

    private static Language pageLanguage(String value) {
        for (Language language : Language.values()) {
            if (language.xmlValue().equals(value)) {
                return language;
            }
        }
        for (Language language : Language.values()) {
            if (language.xmlValue().equalsIgnoreCase(value)) {
                return language;
            }
        }
        return null;
    }

    private static Language isoLanguage(String value) {
        if (!value.matches("^[A-Za-z]{2,3}([_-][A-Za-z]{2})?$")) {
            return null;
        }
        String code = value.replace('_', '-').toLowerCase(Locale.ROOT).split("-")[0];
        for (String iso2 : Locale.getISOLanguages()) {
            Locale locale = Locale.forLanguageTag(iso2);
            if (iso2.equals(code) || iso3(locale).equals(code)) {
                return pageLanguage(locale.getDisplayLanguage(Locale.ENGLISH));
            }
        }
        return null;
    }

    private static String iso3(Locale locale) {
        try {
            return locale.getISO3Language();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static <T> T value(String raw, Function<String, T> fromXml, String context) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        try {
            return fromXml.apply(normalized);
        } catch (IllegalArgumentException e) {
            throw new PageDtoMappingException(context + ": " + e.getMessage(), e);
        }
    }

    private static <T, R> List<R> each(List<T> source, Function<T, R> mapper) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<R> result = new ArrayList<>(source.size());
        for (T item : source) {
            if (item != null) {
                result.add(mapper.apply(item));
            }
        }
        return result;
    }

    private static <T> void forEach(List<T> source, java.util.function.Consumer<T> action) {
        if (source == null) {
            return;
        }
        for (T item : source) {
            if (item != null) {
                action.accept(item);
            }
        }
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first;
        }
        return hasText(second) ? second : null;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
