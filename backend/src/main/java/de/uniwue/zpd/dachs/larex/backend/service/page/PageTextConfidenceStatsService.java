package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PageTextConfidenceStatsService {

    private static final int PAGE_ID_CHUNK_SIZE = 1_000;

    private final PageConfidenceIndexRepository pageConfidenceIndexRepository;

    public PageTextConfidenceStatsService(PageConfidenceIndexRepository pageConfidenceIndexRepository) {
        this.pageConfidenceIndexRepository = pageConfidenceIndexRepository;
    }

    public Map<String, PageDto.TextConfidenceStats> resolveStats(String projectId, Collection<Page> pages) {
        if (projectId == null || projectId.isBlank() || pages == null || pages.isEmpty()) {
            return Map.of();
        }

        List<String> pageIds = pages.stream()
                .map(Page::getId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
        if (pageIds.isEmpty()) {
            return Map.of();
        }

        Map<String, PageDto.TextConfidenceStats> statsByPageId = new HashMap<>();
        // Chunk to stay well below PostgreSQL's bind-parameter limit on very large projects.
        for (int from = 0; from < pageIds.size(); from += PAGE_ID_CHUNK_SIZE) {
            List<String> chunk = pageIds.subList(from, Math.min(from + PAGE_ID_CHUNK_SIZE, pageIds.size()));
            List<Object[]> rows = pageConfidenceIndexRepository.aggregateConfidenceStatsByProjectIdAndPageIds(
                    projectId,
                    chunk,
                    PageConfidenceIndex.ElementType.TEXTEQUIV.name()
            );
            for (Object[] row : rows) {
                if (row.length < 6 || !(row[0] instanceof String pageId)) {
                    continue;
                }
                statsByPageId.put(pageId, new PageDto.TextConfidenceStats(
                        ((Number) row[1]).doubleValue(),
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).doubleValue(),
                        ((Number) row[4]).doubleValue(),
                        ((Number) row[5]).intValue()
                ));
            }
        }

        return statsByPageId;
    }
}
