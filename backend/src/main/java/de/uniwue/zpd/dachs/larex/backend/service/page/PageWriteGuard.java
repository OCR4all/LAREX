package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PageWriteGuard {

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;

    public PageWriteGuard(ProjectRepository projectRepository,
                          PageRepository pageRepository) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, String> lockPages(Collection<String> pageIds, boolean canReviewPages) {
        if (pageIds.isEmpty()) {
            return Map.of();
        }
        projectRepository.findByPageIdsForUpdate(pageIds);
        pageRepository.findAllByIdInForUpdate(pageIds);
        Map<String, String> lockReasons = new LinkedHashMap<>();
        for (PageRepository.PageLockState state : pageRepository.findLockStatesByIdIn(pageIds)) {
            Page page = currentPage(state);
            if (page.isLockedFor(canReviewPages)) {
                lockReasons.put(state.getPageId(), withReason("Page is locked", page.getLockedReasonFor(canReviewPages)));
            }
        }
        return lockReasons;
    }

    private static Page currentPage(PageRepository.PageLockState state) {
        Project project = new Project();
        project.setLocked(state.getProjectLocked());
        project.setLockedReason(state.getProjectLockedReason());
        Page page = new Page();
        page.setProject(project);
        page.setLocked(state.getLocked());
        page.setLockedReason(state.getLockedReason());
        page.setWorkflowState(state.getWorkflowState());
        return page;
    }

    @Transactional(readOnly = true)
    public Optional<String> projectLockReason(String projectId) {
        return projectRepository.findById(projectId)
                .filter(Project::isLocked)
                .map(project -> withReason("Project is locked", project.getLockedReason()));
    }

    public static String summarize(Collection<String> lockReasons) {
        return lockReasons.stream()
                .collect(Collectors.groupingBy(reason -> reason, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> entry.getKey() + " (" + entry.getValue() + ")")
                .collect(Collectors.joining(", "));
    }

    private static String withReason(String message, String reason) {
        return reason == null || reason.isBlank() ? message : message + ": " + reason;
    }
}
