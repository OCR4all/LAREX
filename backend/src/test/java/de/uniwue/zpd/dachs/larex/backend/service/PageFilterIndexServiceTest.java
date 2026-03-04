package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.page.text.GlyphDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.WordDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import de.uniwue.zpd.dachs.larex.backend.entity.PageLabelIndex;
import de.uniwue.zpd.dachs.larex.backend.entity.PageTextContent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageFilterIndexServiceTest {

    @Mock
    private PageRepository pageRepository;

    @Mock
    private PageXmlRepository pageXmlRepository;

    @Mock
    private PageTextContentRepository textContentRepository;

    @Mock
    private PageLabelIndexRepository labelIndexRepository;

    @Mock
    private PageConfidenceIndexRepository confidenceIndexRepository;

    @Mock
    private AnnotationProcessingService annotationProcessingService;

    private PageFilterIndexService service;

    @BeforeEach
    void setUp() {
        service = new PageFilterIndexService(
            pageRepository,
            pageXmlRepository,
            textContentRepository,
            labelIndexRepository,
            confidenceIndexRepository,
            annotationProcessingService
        );
    }

    @Test
    void indexPage_generatesCanonicalLabelTokens() {
        Page page = page("page-1", "project-1");
        TextLineDto line = textLine(
            "line-1",
            "readingOrder { index:1; level:body; }",
            List.of(),
            List.of(),
            null
        );

        List<RegionDto> regions = List.of(
            region("r1", RegionKind.TextRegion, "paragraph", null, List.of(), List.of(line), null),
            region("r2", RegionKind.TextRegion, "heading", null, List.of(), List.of(), null),
            region("r3", RegionKind.ImageRegion, "logo", null, List.of(), List.of(), null),
            region("r4", RegionKind.GraphicRegion, null, "structure { type:diagram; }", List.of(), List.of(), null)
        );

        service.indexPage(page, pageDto(regions, null, null));

        ArgumentCaptor<Iterable<PageLabelIndex>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(labelIndexRepository).saveAll(captor.capture());

        Set<String> tokens = streamOf(captor.getValue())
            .stream()
            .map(PageLabelIndex::getLabelId)
            .collect(Collectors.toSet());

        assertTrue(tokens.contains("region|kind=TextRegion"));
        assertTrue(tokens.contains("region|kind=TextRegion|textType=paragraph"));
        assertTrue(tokens.contains("region|kind=TextRegion|textType=heading"));
        assertTrue(tokens.contains("region|kind=ImageRegion|subType=logo"));
        assertTrue(tokens.contains("region|kind=GraphicRegion|subType=diagram"));
        assertTrue(tokens.contains("line|customKey=readingOrder"));
        assertTrue(tokens.contains("line|customKey=readingOrder|pairs=index=1,level=body"));
    }

    @Test
    void indexPage_indexesUnicodeAcrossRegionLineWordAndGlyph() {
        Page page = page("page-1", "project-1");

        GlyphDto glyph = glyph(
            "glyph-1",
            List.of(variant("glyph unicode", 0.55, 0)),
            0.67
        );
        WordDto word = word(
            "word-1",
            List.of(variant("word unicode", 0.66, 0)),
            List.of(glyph),
            0.75
        );
        TextLineDto line = textLine(
            "line-1",
            null,
            List.of(variant("line unicode", 0.77, 0)),
            List.of(word),
            0.81
        );
        RegionDto region = region(
            "region-1",
            RegionKind.TextRegion,
            "paragraph",
            null,
            List.of(variant("region unicode", 0.88, 0)),
            List.of(line),
            0.9
        );

        service.indexPage(page, pageDto(List.of(region), 0.91, null));

        ArgumentCaptor<Iterable<PageTextContent>> textCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(textContentRepository).saveAll(textCaptor.capture());
        List<PageTextContent> rows = streamOf(textCaptor.getValue());

        Set<String> indexedTexts = rows.stream().map(PageTextContent::getTextContent).collect(Collectors.toSet());
        assertTrue(indexedTexts.contains("region unicode"));
        assertTrue(indexedTexts.contains("line unicode"));
        assertTrue(indexedTexts.contains("word unicode"));
        assertTrue(indexedTexts.contains("glyph unicode"));

        PageTextContent regionRow = rows.stream()
            .filter(row -> "region unicode".equals(row.getTextContent()))
            .findFirst()
            .orElseThrow();
        assertEquals(null, regionRow.getTextLineId());

        ArgumentCaptor<Iterable<PageConfidenceIndex>> confidenceCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(confidenceIndexRepository).saveAll(confidenceCaptor.capture());
        Set<PageConfidenceIndex.ElementType> confidenceTypes = streamOf(confidenceCaptor.getValue())
            .stream()
            .map(PageConfidenceIndex::getElementType)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(PageConfidenceIndex.ElementType.class)));

        assertTrue(confidenceTypes.contains(PageConfidenceIndex.ElementType.PAGE));
        assertTrue(confidenceTypes.contains(PageConfidenceIndex.ElementType.TEXTEQUIV));
    }

    @Test
    void filterPages_appliesGlobalAndAcrossGroups() {
        String projectId = "project-1";

        Page taggedPage = page("page-2", projectId);
        taggedPage.setTags(List.of("tag-A"));
        Page otherTaggedPage = page("page-3", projectId);
        otherTaggedPage.setTags(List.of("tag-A"));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(taggedPage, otherTaggedPage));

        when(textContentRepository.findPageIdsByProjectIdAndTextContentContaining(projectId, "needle"))
            .thenReturn(List.of("page-1", "page-2"));
        when(labelIndexRepository.findPageIdsByProjectIdAndAllLabelIds(projectId, List.of("L1", "L2"), 2))
            .thenReturn(List.of("page-2", "page-3"));
        when(confidenceIndexRepository.findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
            eq(projectId),
            eq(0.3),
            eq(0.8),
            anyList()
        )).thenReturn(List.of("page-2", "page-4"));

        Set<String> result = service.filterPages(
            projectId,
            "needle",
            List.of("L1", "L2"),
            "and",
            List.of("tag-A"),
            "and",
            0.3,
            0.8,
            List.of("TEXTEQUIV")
        );

        assertEquals(Set.of("page-2"), result);
    }

    @Test
    void filterPages_appliesGlobalOrAcrossGroups() {
        String projectId = "project-1";

        Page tagPage = page("page-3", projectId);
        tagPage.setTags(List.of("tag-A"));
        when(pageRepository.findByProjectId(projectId)).thenReturn(List.of(tagPage));

        when(textContentRepository.findPageIdsByProjectIdAndTextContentContaining(projectId, "needle"))
            .thenReturn(List.of("page-1"));
        when(labelIndexRepository.findPageIdsByProjectIdAndLabelIdsIn(projectId, List.of("L1")))
            .thenReturn(List.of("page-2"));
        when(confidenceIndexRepository.findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
            eq(projectId),
            anyDouble(),
            anyDouble(),
            anyList()
        )).thenReturn(List.of("page-4"));

        Set<String> result = service.filterPages(
            projectId,
            "needle",
            List.of("L1"),
            "or",
            List.of("tag-A"),
            "or",
            0.1,
            0.9,
            List.of("PAGE")
        );

        assertEquals(Set.of("page-1", "page-2", "page-3", "page-4"), result);
    }

    @Test
    void filterPages_confidenceTypesDefaultToAllWhenNoneSelected() {
        String projectId = "project-1";
        when(confidenceIndexRepository.findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
            eq(projectId),
            eq(0.2),
            eq(0.6),
            anyList()
        )).thenReturn(List.of("page-9"));

        service.filterPages(
            projectId,
            null,
            null,
            "or",
            null,
            "or",
            0.2,
            0.6,
            null
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PageConfidenceIndex.ElementType>> typesCaptor = ArgumentCaptor.forClass((Class<List<PageConfidenceIndex.ElementType>>) (Class<?>) List.class);
        verify(confidenceIndexRepository).findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
            eq(projectId),
            eq(0.2),
            eq(0.6),
            typesCaptor.capture()
        );

        Set<PageConfidenceIndex.ElementType> actual = new HashSet<>(typesCaptor.getValue());
        assertEquals(EnumSet.allOf(PageConfidenceIndex.ElementType.class), actual);
    }

    @Test
    void filterPages_confidenceOnlyReturnsEmptyWhenNoMatch() {
        String projectId = "project-1";
        when(confidenceIndexRepository.findPageIdsByProjectIdAndConfidenceRangeAndElementTypes(
            eq(projectId),
            eq(0.4),
            eq(0.5),
            anyList()
        )).thenReturn(Collections.emptyList());

        Set<String> result = service.filterPages(
            projectId,
            null,
            null,
            "and",
            null,
            "and",
            0.4,
            0.5,
            List.of("COORDS")
        );

        assertTrue(result.isEmpty());
    }

    @Test
    void getMatchingTextLineIds_skipsBlankQueries() {
        List<String> result = service.getMatchingTextLineIds("page-1", "   ");

        assertTrue(result.isEmpty());
        verify(textContentRepository, never()).findTextLineIdsByPageIdAndTextContentContaining(eq("page-1"), eq("   "));
    }

    private static Page page(String pageId, String projectId) {
        Project project = new Project();
        project.setId(projectId);

        Page page = new Page();
        page.setId(pageId);
        page.setProject(project);
        page.setTags(new ArrayList<>());
        return page;
    }

    private static PageDto pageDto(List<RegionDto> regions, Double confidence, ReadingOrderDto readingOrder) {
        return new PageDto(
            "image.png",
            1000,
            1000,
            null,
            null,
            null,
            null,
            "pc-1",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            confidence,
            null,
            null,
            regions,
            readingOrder,
            null,
            null
        );
    }

    private static RegionDto region(
            String id,
            RegionKind kind,
            String type,
            String custom,
            List<TextContentVariantDto> textVariants,
            List<TextLineDto> textLines,
            Double confidence) {
        return new RegionDto(
            id,
            kind,
            null,
            textLines,
            textVariants,
            type,
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
            confidence,
            custom,
            null,
            null,
            null
        );
    }

    private static TextLineDto textLine(
            String id,
            String custom,
            List<TextContentVariantDto> variants,
            List<WordDto> words,
            Double confidence) {
        return new TextLineDto(
            id,
            null,
            null,
            variants,
            words,
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
            confidence,
            null,
            custom,
            null
        );
    }

    private static WordDto word(
            String id,
            List<TextContentVariantDto> variants,
            List<GlyphDto> glyphs,
            Double confidence) {
        return new WordDto(
            id,
            null,
            variants,
            glyphs,
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
            confidence,
            null,
            null
        );
    }

    private static GlyphDto glyph(String id, List<TextContentVariantDto> variants, Double confidence) {
        return new GlyphDto(
            id,
            null,
            variants,
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
            confidence,
            null,
            null
        );
    }

    private static TextContentVariantDto variant(String unicode, Double confidence, Integer index) {
        return new TextContentVariantDto(unicode, null, confidence, index, null, null, null);
    }

    private static <T> List<T> streamOf(Iterable<T> iterable) {
        return StreamSupport.stream(iterable.spliterator(), false).toList();
    }
}
