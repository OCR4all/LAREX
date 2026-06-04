package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PageTextConfidenceStatsService {

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

        List<Object[]> rows = pageConfidenceIndexRepository.findConfidenceValuesByProjectIdAndPageIdsAndElementType(
                projectId,
                pageIds,
                PageConfidenceIndex.ElementType.TEXTEQUIV
        );

        Map<String, List<Double>> valuesByPageId = new HashMap<>();
        for (Object[] row : rows) {
            if (row.length < 2 || !(row[0] instanceof String pageId) || !(row[1] instanceof Number confidence)) {
                continue;
            }
            valuesByPageId.computeIfAbsent(pageId, ignored -> new ArrayList<>()).add(confidence.doubleValue());
        }

        Map<String, PageDto.TextConfidenceStats> statsByPageId = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : valuesByPageId.entrySet()) {
            List<Double> values = entry.getValue().stream().sorted().toList();
            if (values.isEmpty()) {
                continue;
            }

            int count = values.size();
            double min = values.getFirst();
            double max = values.getLast();
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
            double median = count % 2 == 1
                    ? values.get(count / 2)
                    : (values.get((count / 2) - 1) + values.get(count / 2)) / 2.0d;

            statsByPageId.put(entry.getKey(), new PageDto.TextConfidenceStats(min, max, mean, median, count));
        }

        return statsByPageId;
    }
}
