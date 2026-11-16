package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.geometry.Point;
import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.Label;
import com.maxnth.page4j.model.LabelGroup;
import com.maxnth.page4j.model.Metadata;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.Relation;
import com.maxnth.page4j.model.TextEquiv;
import com.maxnth.page4j.model.TextLineBuilder;
import com.maxnth.page4j.model.TextRegion;
import com.maxnth.page4j.model.TextRegionBuilder;
import com.maxnth.page4j.model.value.PageDateTime;
import com.maxnth.page4j.model.value.RelationType;
import com.maxnth.page4j.model.value.TextType;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class Page4jToDtoMapperTest {

    private final Page4jToDtoMapper mapper = new Page4jToDtoMapper();

    @Test
    void toDto_leavesAbsentAttributesAbsent() {
        var dto = mapper.toDto(page(List.of(region("r1", 0).build()), List.of()), PageXmlVersion.V2019_07_15);

        assertEquals(1, dto.regions().size());
        assertNull(dto.regions().get(0).type());
        assertNull(dto.regions().get(0).continuation());
        assertNull(dto.regions().get(0).textStyle());
        assertEquals("2019-07-15", dto.formatVersion());
    }

    @Test
    void toDto_keepsMetadataAndTextRegionTypeWhenPresent() {
        Page page = page(List.of(region("r1", 0).type(TextType.PARAGRAPH).continuation(false).build()), List.of())
            .withMetadata(new Metadata("ext", "tester", PageDateTime.parse("2025-01-01T10:15:00"),
                PageDateTime.parse("2025-01-01T11:30:00+01:00"), "comment", List.of(), List.of()));

        var dto = mapper.toDto(page, PageXmlVersion.V2019_07_15);

        assertEquals("paragraph", dto.regions().get(0).type());
        assertEquals(false, dto.regions().get(0).continuation());
        assertEquals("tester", dto.metadata().creator());
        assertEquals("comment", dto.metadata().comments());
        assertEquals("ext", dto.metadata().externalRef());
        assertEquals("2025-01-01T10:15:00", dto.metadata().created());
        assertEquals("2025-01-01T11:30:00+01:00", dto.metadata().lastChange());
    }

    @Test
    void toDto_keepsMissingTextEquivConfidenceAsNull() {
        TextRegion region = region("r1", 0)
            .textLines(List.of(TextLineBuilder.builder()
                .id("tl1")
                .coords(rectangle(0))
                .textEquivs(List.of(
                    new TextEquiv(null, null, null, null, null, null, "without-conf"),
                    new TextEquiv(null, 0.87, null, null, null, null, "with-conf")))
                .build()))
            .build();

        var variants = mapper.toDto(page(List.of(region), List.of()), PageXmlVersion.V2019_07_15)
            .regions().get(0).textLines().get(0).textContentVariants();

        assertEquals(2, variants.size());
        assertNull(variants.get(0).confidence());
        assertEquals(0.87, variants.get(1).confidence());
    }

    @Test
    void toDto_exportsRelationsWithFieldsAndLabelsInDocumentOrder() {
        Page page = page(
            List.of(region("r1", 0).build(), region("r2", 200).build()),
            List.of(
                new Relation("rel-2", RelationType.JOIN, "custom-field", "relation-comment",
                    List.of(new LabelGroup("model-a", null, null, null, List.of(new Label("caption", "semantic", "label-comment")))),
                    "r1", "r2"),
                new Relation("rel-1", RelationType.LINK, null, null, List.of(), "r2", "r1")));

        var relations = mapper.toDto(page, PageXmlVersion.V2019_07_15).relations().relations();

        assertEquals(2, relations.size());
        assertEquals("rel-2", relations.get(0).id());
        assertEquals("join", relations.get(0).type());
        assertEquals("r1", relations.get(0).sourceRegionRef());
        assertEquals("r2", relations.get(0).targetRegionRef());
        assertEquals("custom-field", relations.get(0).custom());
        assertEquals("relation-comment", relations.get(0).comments());
        assertNotNull(relations.get(0).labels());
        assertEquals("caption", relations.get(0).labels().get(0).labels().get(0).value());
        assertEquals("rel-1", relations.get(1).id());
        assertEquals("link", relations.get(1).type());
    }

    private static Page page(List<TextRegion> regions, List<Relation> relations) {
        return Page.builder()
            .metadata(new Metadata(null, "", PageDateTime.parse("2020-01-01T00:00:00"), PageDateTime.parse("2020-01-01T00:00:00"), null, List.of(), List.of()))
            .imageFilename("image.png")
            .imageWidth(1200)
            .imageHeight(1800)
            .regions(regions)
            .relations(relations)
            .build();
    }

    private static TextRegionBuilder region(String id, int offsetX) {
        return TextRegionBuilder.builder().id(id).coords(rectangle(offsetX));
    }

    private static Polygon rectangle(int offsetX) {
        return Polygon.of(new Point(offsetX, 0), new Point(offsetX + 100, 0), new Point(offsetX + 100, 100), new Point(offsetX, 100));
    }
}
