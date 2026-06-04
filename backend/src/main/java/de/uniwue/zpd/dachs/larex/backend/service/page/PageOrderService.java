package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PageOrderService {

    public static final int SORT_ORDER_STEP = 1000;

    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public PageOrderService(PageRepository pageRepository,
                            ProjectRepository projectRepository,
                            WorkspaceAccessService workspaceAccessService) {
        this.pageRepository = pageRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
    }

    public Comparator<Page> projectOrderComparator() {
        return Comparator
                .comparing((Page page) -> page.getSortOrder() == null ? 0 : 1)
                .thenComparing(Page::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Page::getName, PageOrderService::compareNaturalIgnoreCase)
                .thenComparing(Page::getId, Comparator.nullsLast(String::compareTo));
    }

    public List<Page> sortPages(Collection<Page> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        return pages.stream()
                .sorted(projectOrderComparator())
                .toList();
    }

    public List<Integer> reserveAppendSortOrders(String projectId, int count) {
        if (count <= 0) {
            return List.of();
        }

        List<Page> pages = pageRepository.findByProjectIdForUpdate(projectId);
        int max = pages.stream()
                .map(Page::getSortOrder)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-SORT_ORDER_STEP);

        List<Integer> orders = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            orders.add(max + ((index + 1) * SORT_ORDER_STEP));
        }
        return orders;
    }

    public Optional<List<Page>> reorderProjectPages(String projectId, List<String> orderedPageIds, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return Optional.empty();
        }

        Project project = projectOpt.get();
        String workspaceId = project.getLibrary().getWorkspaceId();
        if (!workspaceAccessService.canManageProjects(workspaceId, userId)) {
            return Optional.empty();
        }
        if (project.isLocked()) {
            throw new IllegalStateException("Project is locked");
        }

        List<Page> pages = pageRepository.findByProjectIdForUpdate(projectId);
        if (pages.stream().anyMatch(Page::isLocked)) {
            throw new IllegalStateException("One or more pages are locked");
        }

        validateCompleteOrder(pages, orderedPageIds);

        var pagesById = pages.stream().collect(java.util.stream.Collectors.toMap(Page::getId, page -> page));
        for (int index = 0; index < orderedPageIds.size(); index++) {
            Page page = pagesById.get(orderedPageIds.get(index));
            page.setSortOrder(index * SORT_ORDER_STEP);
        }

        pageRepository.saveAll(pages);
        return Optional.of(sortPages(pages));
    }

    private void validateCompleteOrder(List<Page> pages, List<String> orderedPageIds) {
        if (orderedPageIds == null) {
            throw new IllegalArgumentException("pageIds is required");
        }
        if (orderedPageIds.size() != pages.size()) {
            throw new IllegalArgumentException("pageIds must contain every project page exactly once");
        }

        Set<String> expected = pages.stream()
                .map(Page::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> seen = new HashSet<>();
        for (String pageId : orderedPageIds) {
            if (pageId == null || pageId.isBlank()) {
                throw new IllegalArgumentException("pageIds must not contain blank values");
            }
            if (!seen.add(pageId)) {
                throw new IllegalArgumentException("pageIds must not contain duplicate values");
            }
            if (!expected.contains(pageId)) {
                throw new IllegalArgumentException("pageIds contains a page outside this project");
            }
        }
    }

    private static int compareNaturalIgnoreCase(String left, String right) {
        String a = left == null ? "" : left.toLowerCase(Locale.ROOT);
        String b = right == null ? "" : right.toLowerCase(Locale.ROOT);
        int ai = 0;
        int bi = 0;

        while (ai < a.length() && bi < b.length()) {
            char ac = a.charAt(ai);
            char bc = b.charAt(bi);
            if (Character.isDigit(ac) && Character.isDigit(bc)) {
                int nextAi = consumeDigits(a, ai);
                int nextBi = consumeDigits(b, bi);
                int numberComparison = compareDigitRuns(a.substring(ai, nextAi), b.substring(bi, nextBi));
                if (numberComparison != 0) {
                    return numberComparison;
                }
                ai = nextAi;
                bi = nextBi;
                continue;
            }
            if (ac != bc) {
                return Character.compare(ac, bc);
            }
            ai++;
            bi++;
        }

        return Integer.compare(a.length(), b.length());
    }

    private static int consumeDigits(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int compareDigitRuns(String left, String right) {
        String normalizedLeft = stripLeadingZeroes(left);
        String normalizedRight = stripLeadingZeroes(right);
        int lengthComparison = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        if (lengthComparison != 0) {
            return lengthComparison;
        }
        int valueComparison = normalizedLeft.compareTo(normalizedRight);
        if (valueComparison != 0) {
            return valueComparison;
        }
        return Integer.compare(left.length(), right.length());
    }

    private static String stripLeadingZeroes(String value) {
        int index = 0;
        while (index < value.length() - 1 && value.charAt(index) == '0') {
            index++;
        }
        return value.substring(index);
    }
}
