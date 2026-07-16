package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PageWorkflowService {

    private final PageRepository pageRepository;
    private final ProjectRepository projectRepository;
    private final TaskPageLinkRepository taskPageLinkRepository;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final AnnotationLeaseService annotationLeaseService;

    public PageWorkflowService(PageRepository pageRepository,
                               ProjectRepository projectRepository,
                               TaskPageLinkRepository taskPageLinkRepository,
                               AuthorizationPolicyService authorizationPolicyService,
                               AnnotationLeaseService annotationLeaseService) {
        this.pageRepository = pageRepository;
        this.projectRepository = projectRepository;
        this.taskPageLinkRepository = taskPageLinkRepository;
        this.authorizationPolicyService = authorizationPolicyService;
        this.annotationLeaseService = annotationLeaseService;
    }

    public Page updateState(String projectId, String pageId, Page.WorkflowState workflowState, String userId) {
        requireStateChangeAccess(projectId, userId);
        Page page = pageRepository.findByIdAndProjectIdForUpdate(pageId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Page", pageId));
        assertNoTransientLock(page);
        if (workflowState == Page.WorkflowState.DONE) {
            annotationLeaseService.assertNoOtherActiveEditor(pageId, userId);
        }
        page.setWorkflowState(workflowState);
        return pageRepository.save(page);
    }

    public List<Page> bulkUpdateState(String projectId,
                                      Collection<String> requestedPageIds,
                                      Page.WorkflowState workflowState,
                                      String userId) {
        requireStateChangeAccess(projectId, userId);
        List<String> pageIds = normalizeIds(requestedPageIds);
        List<Page> pages = pageRepository.findByIdInAndProjectIdForUpdate(pageIds, projectId);
        if (pages.size() != pageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every page must belong to the selected project");
        }
        pages.forEach(this::assertNoTransientLock);
        if (workflowState == Page.WorkflowState.DONE) {
            pages.forEach(page -> annotationLeaseService.assertNoOtherActiveEditor(page.getId(), userId));
        }
        pages.forEach(page -> page.setWorkflowState(workflowState));
        return pageRepository.saveAll(pages);
    }

    public List<Page> recomputeForPageIds(Collection<String> requestedPageIds) {
        List<String> pageIds = normalizeIds(requestedPageIds);
        if (pageIds.isEmpty()) {
            return List.of();
        }

        List<Page> pages = pageRepository.findAllByIdInForUpdate(pageIds);
        if (pages.size() != pageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more linked pages no longer exist");
        }
        return recomputeForPages(pages);
    }

    public List<Page> recomputeForExistingPageIds(Collection<String> requestedPageIds) {
        List<String> pageIds = normalizeIds(requestedPageIds);
        if (pageIds.isEmpty()) {
            return List.of();
        }

        return recomputeForPages(pageRepository.findAllByIdInForUpdate(pageIds));
    }

    private List<Page> recomputeForPages(List<Page> pages) {
        if (pages.isEmpty()) {
            return List.of();
        }

        pages.forEach(this::assertNoTransientLock);
        List<String> pageIds = pages.stream().map(Page::getId).toList();

        Map<String, List<Task>> tasksByPageId = new LinkedHashMap<>();
        for (Object[] row : taskPageLinkRepository.findSyncEnabledTasksByPageIds(pageIds)) {
            if (row != null && row.length >= 2 && row[0] instanceof String pageId && row[1] instanceof Task task) {
                tasksByPageId.computeIfAbsent(pageId, ignored -> new ArrayList<>()).add(task);
            }
        }

        for (Page page : pages) {
            Page.WorkflowState state = resolveState(tasksByPageId.getOrDefault(page.getId(), List.of()));
            if (state == Page.WorkflowState.DONE) {
                annotationLeaseService.assertNoOtherActiveEditor(page.getId(), null);
            }
            page.setWorkflowState(state);
        }
        return pageRepository.saveAll(pages);
    }

    private Page.WorkflowState resolveState(List<Task> linkedTasks) {
        List<Task> participating = linkedTasks.stream()
                .filter(task -> task.getStatus() != Task.TaskStatus.CANCELLED)
                .toList();
        if (participating.isEmpty()) {
            return Page.WorkflowState.OPEN;
        }
        if (participating.stream().allMatch(task -> task.getStatus() == Task.TaskStatus.COMPLETED)) {
            return Page.WorkflowState.DONE;
        }
        if (participating.stream().anyMatch(task -> task.getStatus() == Task.TaskStatus.IN_PROGRESS)) {
            return Page.WorkflowState.IN_PROGRESS;
        }
        return Page.WorkflowState.OPEN;
    }

    private void requireStateChangeAccess(String projectId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        String workspaceId = project.getLibrary().getWorkspaceId();
        if (!authorizationPolicyService.canAccessWorkspace(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Editor access is required to change page state");
        }
        if (project.isLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    project.getLockedReason() == null ? "Project is locked" : project.getLockedReason());
        }
    }

    private void assertNoTransientLock(Page page) {
        if (page.getProject() != null && page.getProject().isLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    page.getProject().getLockedReason() == null ? "Project is locked" : page.getProject().getLockedReason());
        }
        if (page.isLocked()) {
            throw new ResponseStatusException(HttpStatus.LOCKED,
                    page.getLockedReason() == null ? "Page is locked" : page.getLockedReason());
        }
    }

    private List<String> normalizeIds(Collection<String> requestedIds) {
        if (requestedIds == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String id : requestedIds) {
            if (id != null && !id.isBlank()) {
                normalized.add(id);
            }
        }
        return List.copyOf(normalized);
    }
}
