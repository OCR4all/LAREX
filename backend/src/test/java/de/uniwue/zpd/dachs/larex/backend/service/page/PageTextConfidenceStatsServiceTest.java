package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageConfidenceIndex;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageTextConfidenceStatsServiceTest {

    @Mock
    private PageConfidenceIndexRepository pageConfidenceIndexRepository;

    @Test
    void resolveStatsComputesTextEquivAggregates() {
        PageTextConfidenceStatsService service = new PageTextConfidenceStatsService(pageConfidenceIndexRepository);
        Page first = page("page-1");
        Page second = page("page-2");

        when(pageConfidenceIndexRepository.findConfidenceValuesByProjectIdAndPageIdsAndElementType(
                eq("project-1"),
                anyCollection(),
                eq(PageConfidenceIndex.ElementType.TEXTEQUIV)
        )).thenReturn(List.of(
                new Object[] {"page-1", 0.2d},
                new Object[] {"page-1", 0.4d},
                new Object[] {"page-1", 0.9d},
                new Object[] {"page-2", 0.5d},
                new Object[] {"page-2", 0.7d}
        ));

        Map<String, PageDto.TextConfidenceStats> stats = service.resolveStats("project-1", List.of(first, second));

        assertThat(stats.get("page-1")).isEqualTo(new PageDto.TextConfidenceStats(0.2d, 0.9d, 0.5d, 0.4d, 3));
        assertThat(stats.get("page-2")).isEqualTo(new PageDto.TextConfidenceStats(0.5d, 0.7d, 0.6d, 0.6d, 2));
    }

    private Page page(String id) {
        Page page = new Page();
        page.setId(id);
        page.setName(id);
        return page;
    }
}
