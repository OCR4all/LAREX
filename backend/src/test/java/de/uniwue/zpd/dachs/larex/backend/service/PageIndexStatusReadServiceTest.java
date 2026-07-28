package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusReadService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageIndexStatusReadServiceTest {

    @Mock
    private PageConfidenceIndexRepository pageConfidenceIndexRepository;

    @Mock
    private PageTextContentRepository pageTextContentRepository;

    @Mock
    private PageLabelIndexRepository pageLabelIndexRepository;

    @Mock
    private PageXmlRepository pageXmlRepository;

    private PageIndexStatusTracker tracker;
    private PageIndexStatusReadService service;

    @BeforeEach
    void setUp() {
        tracker = new PageIndexStatusTracker();
        service = new PageIndexStatusReadService(
                pageConfidenceIndexRepository,
                pageTextContentRepository,
                pageLabelIndexRepository,
                pageXmlRepository,
                tracker
        );
    }

    @Test
    void resolveStatusesForProjectPages_appliesPriorityRules() {
        Page noXml = page("p1");
        Page unindexed = page("p2");
        Page indexing = page("p3");
        Page indexed = page("p4");

        tracker.markIndexingIfAbsent(indexing.getId());
        when(pageConfidenceIndexRepository.findIndexedPageIdsByProjectIdAndPageIds("project-1", List.of("p1", "p2", "p3", "p4")))
                .thenReturn(List.of("p3", "p4"));
        when(pageTextContentRepository.findIndexedPageIdsByProjectIdAndPageIds("project-1", List.of("p1", "p2", "p3", "p4")))
                .thenReturn(List.of());
        when(pageLabelIndexRepository.findIndexedPageIdsByProjectIdAndPageIds("project-1", List.of("p1", "p2", "p3", "p4")))
                .thenReturn(List.of());
        when(pageXmlRepository.findByPage_IdIn(List.of("p1", "p2", "p3", "p4")))
                .thenReturn(List.of(head(unindexed), head(indexing), head(indexed)));

        Map<String, PageDto.PageIndexingStatus> result = service.resolveStatusesForProjectPages(
                "project-1",
                List.of(noXml, unindexed, indexing, indexed)
        );

        assertEquals(PageDto.PageIndexingStatus.NOT_APPLICABLE, result.get("p1"));
        assertEquals(PageDto.PageIndexingStatus.UNINDEXED, result.get("p2"));
        assertEquals(PageDto.PageIndexingStatus.INDEXING, result.get("p3"));
        assertEquals(PageDto.PageIndexingStatus.INDEXED, result.get("p4"));
    }

    @Test
    void resolveStatusForPage_usesTrackerBeforeIndexedRows() {
        Page page = page("p-indexing");
        tracker.markIndexingIfAbsent(page.getId());
        when(pageXmlRepository.existsByPage_Id(page.getId())).thenReturn(true);

        PageDto.PageIndexingStatus status = service.resolveStatusForPage(page);

        assertEquals(PageDto.PageIndexingStatus.INDEXING, status);
    }

    private Page page(String id) {
        Page page = new Page();
        page.setId(id);
        return page;
    }

    private PageXml head(Page page) {
        PageXml pageXml = new PageXml();
        pageXml.setPage(page);
        return pageXml;
    }
}
