package de.uniwue.zpd.dachs.larex.backend.service.page.indexing;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
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
    private final PageTextContentRepository pageTextContentRepository;
    private final PageLabelIndexRepository pageLabelIndexRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageIndexStatusTracker pageIndexStatusTracker;

    public PageIndexStatusReadService(PageConfidenceIndexRepository pageConfidenceIndexRepository,
                                      PageTextContentRepository pageTextContentRepository,
                                      PageLabelIndexRepository pageLabelIndexRepository,
                                      PageXmlRepository pageXmlRepository,
                                      PageIndexStatusTracker pageIndexStatusTracker) {
        this.pageConfidenceIndexRepository = pageConfidenceIndexRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.pageLabelIndexRepository = pageLabelIndexRepository;
        this.pageXmlRepository = pageXmlRepository;
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

        Set<String> indexedPageIds = indexedPageIds(projectId, pageIds);
        Set<String> indexingPageIds = pageIndexStatusTracker.filterIndexing(pageIds);
        Set<String> pageIdsWithXml = pageXmlRepository.findByPage_IdIn(pageIds).stream()
                .map(pageXml -> pageXml.getPage().getId())
                .collect(Collectors.toSet());

        Map<String, PageDto.PageIndexingStatus> statuses = new LinkedHashMap<>();
        for (Page page : pages) {
            if (page == null || page.getId() == null) {
                continue;
            }
            statuses.put(page.getId(), resolveStatus(page, pageIdsWithXml, indexedPageIds, indexingPageIds));
        }
        return statuses;
    }

    public PageDto.PageIndexingStatus resolveStatusForPage(Page page) {
        if (page == null || page.getId() == null) {
            return PageDto.PageIndexingStatus.NOT_APPLICABLE;
        }

        if (!pageXmlRepository.existsByPage_Id(page.getId())) {
            return PageDto.PageIndexingStatus.NOT_APPLICABLE;
        }

        if (pageIndexStatusTracker.isIndexing(page.getId())) {
            return PageDto.PageIndexingStatus.INDEXING;
        }

        if (pageConfidenceIndexRepository.existsByPageId(page.getId())
                || pageTextContentRepository.existsByPageId(page.getId())
                || pageLabelIndexRepository.existsByPageId(page.getId())) {
            return PageDto.PageIndexingStatus.INDEXED;
        }

        return PageDto.PageIndexingStatus.UNINDEXED;
    }

    private Set<String> indexedPageIds(String projectId, List<String> pageIds) {
        if (pageIds.isEmpty()) {
            return Set.of();
        }

        Set<String> indexedPageIds = pageConfidenceIndexRepository.findIndexedPageIdsByProjectIdAndPageIds(projectId, pageIds).stream()
                .collect(Collectors.toSet());
        indexedPageIds.addAll(pageTextContentRepository.findIndexedPageIdsByProjectIdAndPageIds(projectId, pageIds));
        indexedPageIds.addAll(pageLabelIndexRepository.findIndexedPageIdsByProjectIdAndPageIds(projectId, pageIds));
        return indexedPageIds;
    }

    private PageDto.PageIndexingStatus resolveStatus(Page page,
                                                     Set<String> pageIdsWithXml,
                                                     Set<String> indexedPageIds,
                                                     Set<String> indexingPageIds) {
        if (!pageIdsWithXml.contains(page.getId())) {
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
