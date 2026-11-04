package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent.Action;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageWorkflowEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.PageWorkflowPublisher;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.task.TaskActivityService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final SubtaskRepository subtaskRepository;
    private final TaskRepository taskRepository;
    private final PageWorkflowEventRepository eventRepository;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final AnnotationLeaseService annotationLeaseService;
    private final TaskActivityService taskActivityService;
    private final TaskQueueRealtimePublisher taskQueueRealtimePublisher;
    private final PageWorkflowPublisher pageWorkflowPublisher;
    private final UserService userService;

    public PageWorkflowService(PageRepository pageRepository,
                               ProjectRepository projectRepository,
                               TaskPageLinkRepository taskPageLinkRepository,
                               SubtaskRepository subtaskRepository,
                               TaskRepository taskRepository,
                               PageWorkflowEventRepository eventRepository,
                               AuthorizationPolicyService authorizationPolicyService,
                               AnnotationLeaseService annotationLeaseService,
                               TaskActivityService taskActivityService,
                               TaskQueueRealtimePublisher taskQueueRealtimePublisher,
                               PageWorkflowPublisher pageWorkflowPublisher,
                               UserService userService) {
        this.pageRepository = pageRepository;
        this.projectRepository = projectRepository;
        this.taskPageLinkRepository = taskPageLinkRepository;
        this.subtaskRepository = subtaskRepository;
        this.taskRepository = taskRepository;
        this.eventRepository = eventRepository;
        this.authorizationPolicyService = authorizationPolicyService;
        this.annotationLeaseService = annotationLeaseService;
        this.taskActivityService = taskActivityService;
        this.taskQueueRealtimePublisher = taskQueueRealtimePublisher;
        this.pageWorkflowPublisher = pageWorkflowPublisher;
        this.userService = userService;
    }

    public List<Page> transition(String projectId,
                                 Collection<String> requestedPageIds,
                                 Action action,
                                 String comment,
                                 String userId) {
        if (action == Action.ASSIGNMENT_SYNC) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This action cannot be requested directly");
        }
        Project project = requireStateChangeAccess(projectId, userId);
        String workspaceId = project.getLibrary().getWorkspaceId();
        boolean canReviewPages = authorizationPolicyService.canReviewPages(workspaceId, userId);
        if ((action == Action.APPROVE || action == Action.REQUEST_CHANGES) && !canReviewPages) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Curator access is required to review pages");
        }

        List<String> pageIds = normalizeIds(requestedPageIds);
        if (pageIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one page");
        }
        List<Page> pages = pageRepository.findByIdInAndProjectIdForUpdate(pageIds, projectId);
        if (pages.size() != pageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every page must belong to the selected project");
        }

        Map<String, List<PageTaskProgress>> progressByPageId = loadTaskProgress(pageIds);
        for (Page page : pages) {
            assertNoTransientLock(page);
            assertTransitionAllowed(page, action, canReviewPages, userId, progressByPageId.getOrDefault(page.getId(), List.of()));
        }
        if (action != Action.REOPEN) {
            pages.forEach(page -> annotationLeaseService.assertNoOtherActiveEditor(page.getId(), userId));
        }

        Set<String> recipients = new LinkedHashSet<>();
        if (action == Action.REQUEST_CHANGES) {
            pages.stream().map(Page::getReviewSubmittedByUserId).filter(Objects::nonNull).forEach(recipients::add);
        }
        String normalizedComment = comment == null || comment.isBlank() ? null : comment.strip();
        Page.WorkflowState target = targetState(action);
        List<PageWorkflowEvent> events = new ArrayList<>();
        for (Page page : pages) {
            events.add(apply(page, action, target, userId, normalizedComment));
        }
        if (target == Page.WorkflowState.OPEN) {
            recipients.addAll(reopenSubtasks(action == Action.REQUEST_CHANGES
                    ? subtaskRepository.findCompletedOnActiveAssignments(pageIds)
                    : subtaskRepository.findCompletedAffectingPageState(pageIds), workspaceId, userId));
        }
        eventRepository.saveAll(events);
        List<Page> saved = pageRepository.saveAll(pages);
        pageWorkflowPublisher.publish(project, saved, action, userId, normalizedComment, recipients);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PageDto.WorkflowEventResponse> listEvents(String projectId, String pageId, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        if (!authorizationPolicyService.canAccessWorkspace(project.getLibrary().getWorkspaceId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access is required");
        }
        pageRepository.findByIdAndProjectId(pageId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Page", pageId));

        List<PageWorkflowEvent> events = eventRepository.findByPageIdOrderByCreatedDesc(pageId);
        Map<String, UserDto> users = userService.getUsersByIds(events.stream()
                .map(PageWorkflowEvent::getActorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return events.stream()
                .map(event -> new PageDto.WorkflowEventResponse(
                        event.getId(),
                        event.getAction(),
                        event.getFromState(),
                        event.getToState(),
                        event.getActorUserId(),
                        event.getActorUserId() == null ? null : users.get(event.getActorUserId()),
                        event.getComment(),
                        event.getCreated()
                ))
                .toList();
    }

    public List<Page> recomputeForPageIds(Collection<String> requestedPageIds) {
        return recomputeForPageIds(requestedPageIds, null);
    }

    public List<Page> recomputeForPageIds(Collection<String> requestedPageIds, String allowedUserId) {
        List<String> pageIds = normalizeIds(requestedPageIds);
        if (pageIds.isEmpty()) {
            return List.of();
        }

        List<Page> pages = pageRepository.findAllByIdInForUpdate(pageIds);
        if (pages.size() != pageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more linked pages no longer exist");
        }
        return recomputeForPages(pages, allowedUserId);
    }

    public List<Page> recomputeForExistingPageIds(Collection<String> requestedPageIds) {
        return recomputeForExistingPageIds(requestedPageIds, null);
    }

    public List<Page> recomputeForExistingPageIds(Collection<String> requestedPageIds, String allowedUserId) {
        List<String> pageIds = normalizeIds(requestedPageIds);
        if (pageIds.isEmpty()) {
            return List.of();
        }

        return recomputeForPages(pageRepository.findAllByIdInForUpdate(pageIds), allowedUserId);
    }

    private List<Page> recomputeForPages(List<Page> pages, String allowedUserId) {
        if (pages.isEmpty()) {
            return List.of();
        }

        pages.forEach(this::assertNoTransientLock);
        Map<String, List<PageTaskProgress>> progressByPageId = loadTaskProgress(pages.stream().map(Page::getId).toList());

        List<Page> changed = new ArrayList<>();
        List<PageWorkflowEvent> events = new ArrayList<>();
        for (Page page : pages) {
            List<PageTaskProgress> participating = progressByPageId.getOrDefault(page.getId(), List.of());
            Page.WorkflowState state = resolveState(page, participating);
            if (state == page.getWorkflowState()) {
                continue;
            }
            if (state != Page.WorkflowState.OPEN) {
                annotationLeaseService.assertNoOtherActiveEditor(page.getId(), allowedUserId);
            }
            events.add(apply(page, Action.ASSIGNMENT_SYNC, state, allowedUserId, null));
            changed.add(page);
        }
        if (changed.isEmpty()) {
            return pages;
        }
        eventRepository.saveAll(events);
        List<Page> saved = pageRepository.saveAll(changed);
        saved.stream()
                .collect(java.util.stream.Collectors.groupingBy(Page::getProject, LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .forEach((project, projectPages) -> pageWorkflowPublisher.publish(
                        project, projectPages, Action.ASSIGNMENT_SYNC, allowedUserId, null, Set.of()));
        return pages;
    }

    private Page.WorkflowState resolveState(Page page, List<PageTaskProgress> participating) {
        Page.WorkflowState current = page.getWorkflowState();
        if (participating.isEmpty()) {
            return current;
        }
        if (participating.stream().anyMatch(progress -> progress.hasOpenSubtask)) {
            return Page.WorkflowState.OPEN;
        }
        if (current != Page.WorkflowState.OPEN) {
            return current;
        }
        return isReviewRequired(page, participating) || page.isChangesRequested()
                ? Page.WorkflowState.IN_REVIEW
                : Page.WorkflowState.DONE;
    }

    private boolean isReviewRequired(Page page, List<PageTaskProgress> participating) {
        return (page.getProject() != null && page.getProject().isReviewEnforced())
                || participating.stream().anyMatch(progress -> progress.task.getPageCompletion() == Task.PageCompletion.REVIEW);
    }

    private Map<String, List<PageTaskProgress>> loadTaskProgress(List<String> pageIds) {
        Map<String, Map<Task, PageTaskProgress>> progressByPageId = new LinkedHashMap<>();
        for (Object[] row : taskPageLinkRepository.findPageStateTaskSubtasksByPageIds(pageIds)) {
            if (row != null
                    && row.length >= 3
                    && row[0] instanceof String pageId
                    && row[1] instanceof Task task
                    && row[2] instanceof Boolean completed
                    && task.getStatus() != Task.TaskStatus.CANCELLED) {
                PageTaskProgress progress = progressByPageId
                        .computeIfAbsent(pageId, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(task, PageTaskProgress::new);
                progress.hasOpenSubtask |= !completed;
            }
        }
        Map<String, List<PageTaskProgress>> result = new LinkedHashMap<>();
        progressByPageId.forEach((pageId, progress) -> result.put(pageId, new ArrayList<>(progress.values())));
        return result;
    }

    private void assertTransitionAllowed(Page page,
                                         Action action,
                                         boolean canReviewPages,
                                         String userId,
                                         List<PageTaskProgress> participating) {
        Page.WorkflowState required = switch (action) {
            case SUBMIT, MARK_DONE -> Page.WorkflowState.OPEN;
            case APPROVE, REQUEST_CHANGES, WITHDRAW -> Page.WorkflowState.IN_REVIEW;
            case REOPEN -> Page.WorkflowState.DONE;
            case ASSIGNMENT_SYNC -> throw new IllegalArgumentException("Assignment sync is not a user transition");
        };
        if (page.getWorkflowState() != required) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Page '" + page.getName() + "' is no longer in the expected state");
        }
        if (action == Action.WITHDRAW && !userId.equals(page.getReviewSubmittedByUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the person who submitted a page can withdraw it");
        }
        if ((action == Action.MARK_DONE || action == Action.REOPEN)
                && !canReviewPages
                && isReviewRequired(page, participating)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Page '" + page.getName() + "' requires review, so only a curator can do this");
        }
    }

    private Page.WorkflowState targetState(Action action) {
        return switch (action) {
            case SUBMIT -> Page.WorkflowState.IN_REVIEW;
            case APPROVE, MARK_DONE -> Page.WorkflowState.DONE;
            case REQUEST_CHANGES, WITHDRAW, REOPEN -> Page.WorkflowState.OPEN;
            case ASSIGNMENT_SYNC -> throw new IllegalArgumentException("Assignment sync has no fixed target");
        };
    }

    private PageWorkflowEvent apply(Page page, Action action, Page.WorkflowState target, String actorUserId, String comment) {
        PageWorkflowEvent event = new PageWorkflowEvent(
                page.getId(), action, page.getWorkflowState(), target, actorUserId, comment);
        boolean inReview = target == Page.WorkflowState.IN_REVIEW;
        page.setWorkflowState(target);
        page.setReviewSubmittedByUserId(inReview ? actorUserId : null);
        page.setReviewSubmittedAt(inReview ? LocalDateTime.now() : null);
        page.setChangesRequested(action == Action.REQUEST_CHANGES);
        return event;
    }

    /**
     * Leaves the page's Subtasks open again, so the Assignment queue and the next recompute agree with the page.
     * Returns the assignees of the reopened Subtasks.
     */
    private Set<String> reopenSubtasks(List<Subtask> subtasks, String workspaceId, String userId) {
        if (subtasks.isEmpty()) {
            return Set.of();
        }
        for (Subtask subtask : subtasks) {
            subtask.setCompleted(false);
            subtask.setCompletedAt(null);
            subtask.setCompletedByUserId(null);
        }
        subtaskRepository.saveAll(subtasks);

        Set<String> taskIds = new LinkedHashSet<>();
        Set<String> assignees = new LinkedHashSet<>();
        for (Subtask subtask : subtasks) {
            taskIds.add(subtask.getTaskId());
            if (subtask.getAssignedUserId() != null) {
                assignees.add(subtask.getAssignedUserId());
            }
        }
        for (Task task : taskRepository.findAllById(taskIds)) {
            if (task.getStatus() == Task.TaskStatus.COMPLETED) {
                task.setStatus(Task.TaskStatus.IN_PROGRESS);
                task.setCompletedAt(null);
                task.setCompletedByUserId(null);
                taskRepository.save(task);
                taskActivityService.logStatusChanged(task.getId(), userId, Task.TaskStatus.COMPLETED, Task.TaskStatus.IN_PROGRESS);
            }
        }
        taskQueueRealtimePublisher.publishAfterCommit(assignees, workspaceId);
        return assignees;
    }

    private static final class PageTaskProgress {
        private final Task task;
        private boolean hasOpenSubtask;

        private PageTaskProgress(Task task) {
            this.task = task;
        }
    }

    private Project requireStateChangeAccess(String projectId, String userId) {
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
        return project;
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
