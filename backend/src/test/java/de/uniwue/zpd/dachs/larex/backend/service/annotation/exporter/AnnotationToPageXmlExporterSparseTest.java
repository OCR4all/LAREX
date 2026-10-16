package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationToPageXmlExporterSparseTest {

    @ParameterizedTest
    @NullSource
    @EmptySource
    void export_doesNotInventTextEquivForNewRegionAndLine(List<TextContentVariantDto> variants) throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        RegionDto region = textRegionWithLines(List.of(textLine(variants)), null, variants);

        String xml = exporter.export(createPageDto(region), null);

        assertTrue(xml.contains("<TextRegion"));
        assertTrue(xml.contains("<TextLine"));
        assertFalse(xml.contains("<TextEquiv"), xml);
    }

    @Test
    void export_preservesExplicitEmptyUnindexedAndIndexedVariants() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        List<TextContentVariantDto> variants = List.of(
            new TextContentVariantDto(""),
            new TextContentVariantDto("", null, 0),
            new TextContentVariantDto("", null, 1)
        );
        RegionDto region = textRegionWithLines(List.of(textLine(variants)), null, variants);

        String xml = exporter.export(createPageDto(region), null);

        assertEquals(6, xml.split("<TextEquiv", -1).length - 1, xml);
        assertEquals(2, xml.split("index=\"0\"", -1).length - 1, xml);
        assertEquals(2, xml.split("index=\"1\"", -1).length - 1, xml);
        assertEquals(6, xml.split("<Unicode", -1).length - 1, xml);
    }

    @Test
    void export_doesNotWriteDefaultTextStyleOrDefaultRegionAttributes() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        PageDto dto = createPageDto(textRegion("paragraph", null, null, null, null, null));

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("\n   <Metadata>"));
        assertTrue(xml.contains("<TextRegion"));
        assertTrue(xml.contains("type=\"paragraph\""));
        assertFalse(xml.contains("<TextStyle"));
        assertFalse(xml.contains("orientation=\"0.0\""));
        assertFalse(xml.contains("indented=\"false\""));
        assertFalse(xml.contains("leading=\"0\""));
        assertFalse(xml.contains("continuation=\"false\""));
    }

    @Test
    void export_writesExplicitZeroAndFalseRegionAttributesWhenProvided() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        PageDto dto = createPageDto(textRegion("paragraph", 0.0, 0, false, false, null));

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("orientation=\"0.0\""));
        assertTrue(xml.contains("leading=\"0\""));
        assertTrue(xml.contains("indented=\"false\""));
        assertTrue(xml.contains("continuation=\"false\""));
    }

    @Test
    void export_doesNotWriteTextEquivIndexWhenVariantIsUnindexed() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        RegionDto region = textRegion("paragraph", null, null, null, null, List.of(
            new TextContentVariantDto("Unindexed text", null, null)
        ));
        PageDto dto = createPageDto(region);

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("<TextEquiv"));
        assertFalse(xml.contains("<TextEquiv index=\""));
    }

    @Test
    void export_writesTextEquivIndexForNewlyAddedIndexedVariants() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        RegionDto region = textRegion("paragraph", null, null, null, null, List.of(
            new TextContentVariantDto("GT", null, 0),
            new TextContentVariantDto("OCR", null, 1)
        ));
        PageDto dto = createPageDto(region);

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("<TextEquiv index=\"0\""));
        assertTrue(xml.contains("<TextEquiv index=\"1\""));
    }

    @Test
    void export_writesBaselineForNewTextLineWhenProvided() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        TextLineDto textLine = new TextLineDto(
            "tl1",
            rectangle(),
            baseline(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        RegionDto region = textRegionWithLines(List.of(textLine), "de");
        PageDto dto = createPageDtoWithMetadata(region, "en", "creator-a", "comment-a", "ref-a");

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("<Baseline"));
        assertTrue(xml.contains("points=\"300,600 700,600\""));
        assertTrue(xml.contains("primaryLanguage=\"English\""));
        assertTrue(xml.contains("<Metadata"));
        assertTrue(xml.contains("<Creator>creator-a</Creator>"));
        assertTrue(xml.contains("<Comments>comment-a</Comments>"));
        assertTrue(xml.contains("externalRef=\"ref-a\""));
        assertTrue(xml.contains("<TextRegion"));
        assertTrue(xml.contains("primaryLanguage=\"German\""));
    }

    @Test
    void export_mapsInternalCustomTextRegionSubtypeToPageOtherAndPreservesStructureType() throws Exception {
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        RegionDto region = new RegionDto(
            "r-custom",
            RegionKind.TextRegion,
            rectangle(),
            null,
            null,
            "custom",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "larex { labelAlias:Foo; labelId:1; } structure { type:foo; }",
            null,
            null
        );
        PageDto dto = createPageDto(region);

        String xml = exporter.export(dto, null);

        assertTrue(xml.contains("<TextRegion"));
        assertTrue(xml.contains("type=\"other\""));
        assertTrue(xml.contains("structure { type:foo; }"));
    }

    private PageDto createPageDto(RegionDto region) {
        return createPageDtoWithMetadata(region, null, "tester", null, null);
    }

    private PageDto createPageDtoWithMetadata(
        RegionDto region,
        String pagePrimaryLanguage,
        String creator,
        String comments,
        String externalRef
    ) {
        return new PageDto(
            "img.png",
            1000,
            1500,
            null,
            null,
            null,
            new MetadataDto(creator, "2026-02-14T10:00:00", "2026-02-14T10:00:00", comments, externalRef),
            null,
            null,
            null,
            null,
            pagePrimaryLanguage,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(region),
            null,
            null
        );
    }

    private RegionDto textRegionWithLines(List<TextLineDto> textLines, String primaryLanguage) {
        return textRegionWithLines(textLines, primaryLanguage, null);
    }

    private RegionDto textRegionWithLines(
        List<TextLineDto> textLines,
        String primaryLanguage,
        List<TextContentVariantDto> variants
    ) {
        return new RegionDto(
            "r1",
            RegionKind.TextRegion,
            rectangle(),
            textLines,
            variants,
            "paragraph",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            primaryLanguage,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private RegionDto textRegion(
        String type,
        Double orientation,
        Integer leading,
        Boolean indented,
        Boolean continuation,
        List<TextContentVariantDto> textContentVariants
    ) {
        return new RegionDto(
            "r1",
            RegionKind.TextRegion,
            rectangle(),
            null,
            textContentVariants,
            type,
            orientation,
            null,
            null,
            null,
            null,
            null,
            leading,
            null,
            null,
            null,
            null,
            indented,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            continuation
        );
    }

    private PolygonDto rectangle() {
        return new PolygonDto(List.of(
            new PointDto(-0.5, -0.5),
            new PointDto(0.5, -0.5),
            new PointDto(0.5, 0.5),
            new PointDto(-0.5, 0.5)
        ), null);
    }

    private TextLineDto textLine(List<TextContentVariantDto> variants) {
        return new TextLineDto(
            "tl1", rectangle(), null, variants, null,
            null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    private PolygonDto baseline() {
        return new PolygonDto(List.of(
            new PointDto(-0.4, 0.2),
            new PointDto(0.4, 0.2)
        ), null);
    }
}
