package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.dto.ReviewQueueDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PageReviewQueueService {

    static final int MAX_QUEUE_PAGES = 500;

    private final PageRepository pageRepository;
    private final TaskPageLinkRepository taskPageLinkRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQueryService workspaceQueryService;
    private final AnnotationLeaseService annotationLeaseService;
    private final UserService userService;

    public PageReviewQueueService(PageRepository pageRepository,
                                  TaskPageLinkRepository taskPageLinkRepository,
                                  TaskRepository taskRepository,
                                  WorkspaceAccessService workspaceAccessService,
                                  WorkspaceQueryService workspaceQueryService,
                                  AnnotationLeaseService annotationLeaseService,
                                  UserService userService) {
        this.pageRepository = pageRepository;
        this.taskPageLinkRepository = taskPageLinkRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQueryService = workspaceQueryService;
        this.annotationLeaseService = annotationLeaseService;
        this.userService = userService;
    }

    public ReviewQueueDto.Response getQueue(String workspaceId, String projectId, String taskId, String submittedBy, String userId) {
        workspaceAccessService.requireReviewPagesAccess(workspaceId, userId);
        String workspaceName = workspaceQueryService.findWorkspaceById(workspaceId)
                .map(workspace -> workspace.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        List<Page> pages = pageRepository.findInReviewByWorkspaceId(workspaceId).stream()
                .filter(page -> projectId == null || projectId.equals(page.getProject().getId()))
                .filter(page -> submittedBy == null || submittedBy.equals(page.getReviewSubmittedByUserId()))
                .toList();
        List<String> pageIds = pages.stream().map(Page::getId).toList();

        Map<String, List<TaskPageLink>> linksByPageId = pageIds.isEmpty()
                ? Map.of()
                : taskPageLinkRepository.findByPageIdIn(pageIds).stream()
                        .collect(Collectors.groupingBy(TaskPageLink::getPageId));
        Map<String, Task> tasksById = taskRepository.findAllById(linksByPageId.values().stream()
                        .flatMap(List::stream)
                        .map(TaskPageLink::getTaskId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Task::getId, task -> task));
        if (taskId != null) {
            pages = pages.stream()
                    .filter(page -> linksByPageId.getOrDefault(page.getId(), List.of()).stream()
                            .anyMatch(link -> taskId.equals(link.getTaskId())))
                    .toList();
        }

        Map<String, List<Task>> tasksByPageId = new LinkedHashMap<>();
        for (Page page : pages) {
            tasksByPageId.put(page.getId(), linksByPageId.getOrDefault(page.getId(), List.of()).stream()
                    .map(link -> tasksById.get(link.getTaskId()))
                    .filter(Objects::nonNull)
                    .filter(task -> task.getStatus() != Task.TaskStatus.CANCELLED)
                    .toList());
        }

        List<Page> sorted = pages.stream()
                .sorted(Comparator
                        .comparing((Page page) -> earliestDueDate(tasksByPageId.get(page.getId())), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(page -> page.getProject().getName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Page::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Page::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Page> window = sorted.stream().limit(MAX_QUEUE_PAGES).toList();

        Map<String, String> activeEditors = annotationLeaseService.findActiveEditors(
                window.stream().map(Page::getId).toList(), userId);
        Map<String, UserDto> submitters = userService.getUsersByIds(window.stream()
                .map(Page::getReviewSubmittedByUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        List<ReviewQueueDto.PageResponse> responses = window.stream()
                .map(page -> {
                    List<Task> tasks = tasksByPageId.get(page.getId());
                    String editor = activeEditors.get(page.getId());
                    String blockedReason = page.isLockedFor(true)
                            ? page.getLockedReasonFor(true)
                            : editor == null ? null : editor + " is reviewing this page";
                    return new ReviewQueueDto.PageResponse(
                            page.getId(),
                            page.getName(),
                            page.getProject().getId(),
                            page.getProject().getName(),
                            page.getSortOrder(),
                            page.getReviewSubmittedByUserId(),
                            page.getReviewSubmittedByUserId() == null ? null : submitters.get(page.getReviewSubmittedByUserId()),
                            page.getReviewSubmittedAt(),
                            earliestDueDate(tasks),
                            tasks.stream().map(task -> new ReviewQueueDto.AssignmentSummary(task.getId(), task.getTitle())).toList(),
                            blockedReason != null,
                            blockedReason
                    );
                })
                .toList();
        return new ReviewQueueDto.Response(
                workspaceId,
                workspaceName,
                sorted.size(),
                responses.stream().filter(ReviewQueueDto.PageResponse::blocked).count(),
                responses
        );
    }

    private LocalDateTime earliestDueDate(List<Task> tasks) {
        return tasks.stream()
                .map(Task::getDueDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
