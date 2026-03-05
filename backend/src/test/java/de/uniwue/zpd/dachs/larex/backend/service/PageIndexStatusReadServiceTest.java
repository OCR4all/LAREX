package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusReadService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageIndexStatusReadServiceTest {

    @Mock
    private PageConfidenceIndexRepository pageConfidenceIndexRepository;

    private PageIndexStatusTracker tracker;
    private PageIndexStatusReadService service;

    @BeforeEach
    void setUp() {
        tracker = new PageIndexStatusTracker();
        service = new PageIndexStatusReadService(pageConfidenceIndexRepository, tracker);
    }

    @Test
    void resolveStatusesForProjectPages_appliesPriorityRules() {
        Page noXml = page("p1", false);
        Page unindexed = page("p2", true);
        Page indexing = page("p3", true);
        Page indexed = page("p4", true);

        tracker.markIndexingIfAbsent(indexing.getId());
        when(pageConfidenceIndexRepository.findIndexedPageIdsByProjectIdAndPageIds("project-1", List.of("p1", "p2", "p3", "p4")))
                .thenReturn(List.of("p3", "p4"));

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
        Page page = page("p-indexing", true);
        tracker.markIndexingIfAbsent(page.getId());

        PageDto.PageIndexingStatus status = service.resolveStatusForPage(page);

        assertEquals(PageDto.PageIndexingStatus.INDEXING, status);
    }

    private Page page(String id, boolean hasXml) {
        Page page = new Page();
        page.setId(id);
        page.setXmlFiles(hasXml ? new HashSet<>(Set.of(new PageXml())) : new HashSet<>());
        return page;
    }
}
