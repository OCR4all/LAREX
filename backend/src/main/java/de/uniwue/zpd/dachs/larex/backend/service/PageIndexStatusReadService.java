package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.PageConfidenceIndexRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PageIndexStatusReadService {

    private final PageConfidenceIndexRepository pageConfidenceIndexRepository;
    private final PageIndexStatusTracker pageIndexStatusTracker;

    public PageIndexStatusReadService(PageConfidenceIndexRepository pageConfidenceIndexRepository,
                                      PageIndexStatusTracker pageIndexStatusTracker) {
        this.pageConfidenceIndexRepository = pageConfidenceIndexRepository;
        this.pageIndexStatusTracker = pageIndexStatusTracker;
    }

    public Map<String, PageDto.PageIndexingStatus> resolveStatusesForProjectPages(String projectId, Collection<Page> pages) {
        if (pages == null || pages.isEmpty()) {
            return Map.of();
        }

        List<String> pageIds = pages.stream()
                .map(Page::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        Set<String> indexedPageIds = pageIds.isEmpty()
                ? Set.of()
                : pageConfidenceIndexRepository.findIndexedPageIdsByProjectIdAndPageIds(projectId, pageIds).stream()
                    .collect(Collectors.toSet());
        Set<String> indexingPageIds = pageIndexStatusTracker.filterIndexing(pageIds);

        Map<String, PageDto.PageIndexingStatus> statuses = new LinkedHashMap<>();
        for (Page page : pages) {
            if (page == null || page.getId() == null) {
                continue;
            }
            statuses.put(page.getId(), resolveStatus(page, indexedPageIds, indexingPageIds));
        }
        return statuses;
    }

    public PageDto.PageIndexingStatus resolveStatusForPage(Page page) {
        if (page == null || page.getId() == null) {
            return PageDto.PageIndexingStatus.NOT_APPLICABLE;
        }

        if (page.getXmlFiles() == null || page.getXmlFiles().isEmpty()) {
            return PageDto.PageIndexingStatus.NOT_APPLICABLE;
        }

        if (pageIndexStatusTracker.isIndexing(page.getId())) {
            return PageDto.PageIndexingStatus.INDEXING;
        }

        return pageConfidenceIndexRepository.existsByPageId(page.getId())
                ? PageDto.PageIndexingStatus.INDEXED
                : PageDto.PageIndexingStatus.UNINDEXED;
    }

    private PageDto.PageIndexingStatus resolveStatus(Page page,
                                                     Set<String> indexedPageIds,
                                                     Set<String> indexingPageIds) {
        if (page.getXmlFiles() == null || page.getXmlFiles().isEmpty()) {
            return PageDto.PageIndexingStatus.NOT_APPLICABLE;
        }
        if (indexingPageIds.contains(page.getId())) {
            return PageDto.PageIndexingStatus.INDEXING;
        }
        if (indexedPageIds.contains(page.getId())) {
            return PageDto.PageIndexingStatus.INDEXED;
        }
        return PageDto.PageIndexingStatus.UNINDEXED;
    }
}
