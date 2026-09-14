package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
import de.uniwue.zpd.dachs.larex.backend.dto.EditorQueueDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserProfileDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class SubtaskService {

    private final SubtaskRepository subtaskRepository;
    private final TaskRepository taskRepository;
    private final PageRepository pageRepository;
    private final TaskActivityService activityService;
    private final UserService userService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQueryService workspaceQueryService;
    private final TaskQueueRealtimePublisher taskQueueRealtimePublisher;

    public SubtaskService(
            SubtaskRepository subtaskRepository,
            TaskRepository taskRepository,
            PageRepository pageRepository,
            TaskActivityService activityService,
            UserService userService,
            AuthorizationPolicyService authorizationPolicyService,
            WorkspaceAccessService workspaceAccessService,
            WorkspaceQueryService workspaceQueryService,
            TaskQueueRealtimePublisher taskQueueRealtimePublisher
    ) {
        this.subtaskRepository = subtaskRepository;
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.activityService = activityService;
        this.userService = userService;
        this.authorizationPolicyService = authorizationPolicyService;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQueryService = workspaceQueryService;
        this.taskQueueRealtimePublisher = taskQueueRealtimePublisher;
    }

    public EditorQueueDto.Response getAssignedEditorQueue(String workspaceId, String userId) {
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this workspace");
        }

        String workspaceName = workspaceQueryService.findWorkspaceById(workspaceId)
                .map(workspace -> workspace.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        List<Subtask> subtasks = subtaskRepository.findOpenAssignedForEditorQueue(workspaceId, userId);
        if (subtasks.isEmpty()) {
            return new EditorQueueDto.Response(workspaceId, workspaceName, 0, 0, 0, List.of());
        }

        List<String> taskIds = subtasks.stream().map(Subtask::getTaskId).distinct().toList();
        Map<String, Task> tasksById = taskRepository.findAllById(taskIds).stream()
                .collect(java.util.stream.Collectors.toMap(Task::getId, task -> task));
        Set<String> pageIds = subtasks.stream()
                .map(Subtask::getPageId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Page> pagesById = pageRepository.findAllByIdIn(pageIds).stream()
                .collect(java.util.stream.Collectors.toMap(Page::getId, page -> page));
        subtasks = subtasks.stream()
                .filter(subtask -> pagesById.containsKey(subtask.getPageId()))
                .toList();
        if (subtasks.isEmpty()) {
            return new EditorQueueDto.Response(workspaceId, workspaceName, 0, 0, 0, List.of());
        }

        Map<String, SubtaskDto.Response> responseById = toResponses(subtasks, tasksById).stream()
                .collect(java.util.stream.Collectors.toMap(SubtaskDto.Response::id, response -> response));
        Map<String, List<Subtask>> subtasksByPage = subtasks.stream()
                .filter(subtask -> pagesById.containsKey(subtask.getPageId()))
                .collect(java.util.stream.Collectors.groupingBy(Subtask::getPageId));

        LocalDateTime now = LocalDateTime.now();
        List<EditorQueueDto.PageResponse> pages = subtasksByPage.entrySet().stream()
                .map(entry -> {
                    Page page = pagesById.get(entry.getKey());
                    List<Subtask> pageSubtasks = entry.getValue();
                    boolean blocked = page.isEffectivelyLocked();
                    return new EditorQueueDto.PageResponse(
                            page.getId(),
                            page.getName(),
                            page.getProject().getId(),
                            page.getProject().getName(),
                            page.getSortOrder(),
                            blocked,
                            blocked ? page.getEffectiveLockedReason() : null,
                            pageSubtasks.stream()
                                    .sorted(Comparator.comparing(Subtask::getSortOrder))
                                    .map(subtask -> responseById.get(subtask.getId()))
                                    .filter(Objects::nonNull)
                                    .toList()
                    );
                })
                .sorted(editorQueueComparator(subtasksByPage, tasksById, pagesById, now))
                .toList();

        long blockedPageCount = pages.stream().filter(EditorQueueDto.PageResponse::blocked).count();
        return new EditorQueueDto.Response(
                workspaceId,
                workspaceName,
                subtasks.size(),
                pages.size(),
                blockedPageCount,
                pages
        );
    }

    private Comparator<EditorQueueDto.PageResponse> editorQueueComparator(
            Map<String, List<Subtask>> subtasksByPage,
            Map<String, Task> tasksById,
            Map<String, Page> pagesById,
            LocalDateTime now
    ) {
        return (left, right) -> {
            List<Subtask> leftSubtasks = subtasksByPage.getOrDefault(left.pageId(), List.of());
            List<Subtask> rightSubtasks = subtasksByPage.getOrDefault(right.pageId(), List.of());
            Comparator<LocalDateTime> dueComparator = Comparator.nullsLast(Comparator.naturalOrder());

            LocalDateTime leftDue = leftSubtasks.stream()
                    .map(subtask -> tasksById.get(subtask.getTaskId()))
                    .filter(Objects::nonNull)
                    .map(Task::getDueDate)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime rightDue = rightSubtasks.stream()
                    .map(subtask -> tasksById.get(subtask.getTaskId()))
                    .filter(Objects::nonNull)
                    .map(Task::getDueDate)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            boolean leftOverdue = leftDue != null && leftDue.isBefore(now);
            boolean rightOverdue = rightDue != null && rightDue.isBefore(now);

            int result = Boolean.compare(rightOverdue, leftOverdue);
            if (result != 0) return result;
            result = dueComparator.compare(leftDue, rightDue);
            if (result != 0) return result;

            int leftPriority = leftSubtasks.stream()
                    .map(subtask -> tasksById.get(subtask.getTaskId()))
                    .filter(Objects::nonNull)
                    .map(Task::getPriority)
                    .mapToInt(this::priorityRank)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            int rightPriority = rightSubtasks.stream()
                    .map(subtask -> tasksById.get(subtask.getTaskId()))
                    .filter(Objects::nonNull)
                    .map(Task::getPriority)
                    .mapToInt(this::priorityRank)
                    .min()
                    .orElse(Integer.MAX_VALUE);
            result = Integer.compare(leftPriority, rightPriority);
            if (result != 0) return result;

            Page leftPage = pagesById.get(left.pageId());
            Page rightPage = pagesById.get(right.pageId());
            result = String.CASE_INSENSITIVE_ORDER.compare(left.projectName(), right.projectName());
            if (result != 0) return result;
            result = Comparator.nullsLast(Comparator.<Integer>naturalOrder()).compare(leftPage.getSortOrder(), rightPage.getSortOrder());
            if (result != 0) return result;
            result = String.CASE_INSENSITIVE_ORDER.compare(left.pageName(), right.pageName());
            return result != 0 ? result : left.pageId().compareTo(right.pageId());
        };
    }

    private int priorityRank(Task.TaskPriority priority) {
        return switch (priority) {
            case URGENT -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    public List<SubtaskDto.Response> getSubtasks(String taskId, String userId) {
        Task task = verifyTaskAccessAndGet(taskId, userId);

        List<Subtask> subtasks = subtaskRepository.findByTaskIdOrderBySortOrderAsc(taskId);
        return toResponses(subtasks, Map.of(task.getId(), task));
    }

    public SubtaskDto.Response createSubtask(String taskId, String userId, SubtaskDto.CreateRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        int sortOrder = subtaskRepository.getNextSortOrder(taskId);

        Subtask subtask = new Subtask(taskId, request.title(), sortOrder);
        subtask.setDescription(request.description());
        subtask = subtaskRepository.save(subtask);

        activityService.logSubtaskAdded(taskId, userId, request.title());
        publishQueueChange(task, subtask.getAssignedUserId());

        return toResponse(subtask, task);
    }

    public SubtaskDto.Response updateSubtask(String taskId, String subtaskId, String userId, SubtaskDto.UpdateRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));

        if (!subtask.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found for this task");
        }

        if (request.title() != null && !request.title().isBlank()) {
            subtask.setTitle(request.title());
        }

        if (request.description() != null) {
            subtask.setDescription(request.description());
        }

        subtask = subtaskRepository.save(subtask);
        publishQueueChange(task, subtask.getAssignedUserId());
        return toResponse(subtask, task);
    }

    public SubtaskDto.Response toggleSubtask(String taskId, String subtaskId, String userId) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));

        if (!subtask.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found for this task");
        }

        boolean wasCompleted = subtask.isCompleted();
        subtask.setCompleted(!wasCompleted);

        if (subtask.isCompleted()) {
            subtask.setCompletedAt(LocalDateTime.now());
            subtask.setCompletedByUserId(userId);
            activityService.logSubtaskCompleted(taskId, userId, subtask.getTitle());
        } else {
            subtask.setCompletedAt(null);
            subtask.setCompletedByUserId(null);
        }

        subtask = subtaskRepository.save(subtask);
        publishQueueChange(task, subtask.getAssignedUserId());
        return toResponse(subtask, task);
    }

    public void reorderSubtasks(String taskId, String userId, SubtaskDto.ReorderRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        List<String> subtaskIds = request.subtaskIds();

        // Verify all subtasks belong to this task
        List<Subtask> subtasks = subtaskRepository.findByTaskIdOrderBySortOrderAsc(taskId);
        Map<String, Subtask> subtaskMap = subtasks.stream()
                .collect(java.util.stream.Collectors.toMap(Subtask::getId, s -> s));

        if (subtaskIds.size() != subtasks.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtask count mismatch");
        }

        for (String id : subtaskIds) {
            if (!subtaskMap.containsKey(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subtask does not belong to this task: " + id);
            }
        }

        // Update sort orders
        IntStream.range(0, subtaskIds.size()).forEach(i -> {
            Subtask subtask = subtaskMap.get(subtaskIds.get(i));
            subtask.setSortOrder(i);
        });
        subtaskRepository.saveAll(subtasks);
        publishQueueChange(task, task.getAssignedUserIds());
    }

    public void deleteSubtask(String taskId, String subtaskId, String userId) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));

        if (!subtask.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found for this task");
        }

        String title = subtask.getTitle();
        subtaskRepository.delete(subtask);

        activityService.logSubtaskDeleted(taskId, userId, title);
        publishQueueChange(task, subtask.getAssignedUserId());
    }

    public SubtaskDto.BulkResponse bulkComplete(String taskId, String userId, SubtaskDto.BulkRequest request) {
        List<String> subtaskIds = normalizeIds(request.subtaskIds());
        List<Subtask> subtasks = subtaskIds.isEmpty()
                ? List.of()
                : subtaskRepository.findByTaskIdAndIdIn(taskId, subtaskIds);
        Task task = verifyTaskCompletionAccess(taskId, userId, subtasks, subtaskIds.size());
        if (subtaskIds.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        List<Subtask> toComplete = subtasks.stream()
                .filter(subtask -> !subtask.isCompleted())
                .toList();
        if (toComplete.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        LocalDateTime completedAt = LocalDateTime.now();
        int affected = subtaskRepository.markCompletedByTaskIdAndIdIn(
                taskId,
                toComplete.stream().map(Subtask::getId).toList(),
                completedAt,
                userId
        );
        for (Subtask subtask : toComplete) {
            activityService.logSubtaskCompleted(taskId, userId, subtask.getTitle());
        }
        publishQueueChange(task, toComplete.stream().map(Subtask::getAssignedUserId).filter(Objects::nonNull).toList());

        return new SubtaskDto.BulkResponse(affected);
    }

    public SubtaskDto.BulkResponse bulkDelete(String taskId, String userId, SubtaskDto.BulkRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        List<String> subtaskIds = normalizeIds(request.subtaskIds());
        if (subtaskIds.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        List<Subtask> subtasks = subtaskRepository.findByTaskIdAndIdIn(taskId, subtaskIds);
        if (subtasks.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        int affected = subtaskRepository.deleteByTaskIdAndIdIn(
                taskId,
                subtasks.stream().map(Subtask::getId).toList()
        );
        for (Subtask subtask : subtasks) {
            activityService.logSubtaskDeleted(taskId, userId, subtask.getTitle());
        }
        publishQueueChange(task, subtasks.stream().map(Subtask::getAssignedUserId).filter(Objects::nonNull).toList());

        return new SubtaskDto.BulkResponse(affected);
    }

    public SubtaskDto.ProgressResponse getProgress(String taskId, String userId) {
        verifyTaskAccess(taskId, userId);

        long total = subtaskRepository.countByTaskId(taskId);
        long completed = subtaskRepository.countCompletedByTaskId(taskId);
        int percentage = total > 0 ? (int) Math.round((completed * 100.0) / total) : 0;

        return new SubtaskDto.ProgressResponse(total, completed, percentage);
    }

    private void verifyTaskAccess(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        boolean hasAccess = task.getCreatedByUserId().equals(userId) ||
                (task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(userId));

        if (!hasAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this task");
        }
    }

    public SubtaskDto.Response createSubtaskWithPage(String taskId, String userId, SubtaskDto.CreateWithPageRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        // Validate assignee is a task assignee
        if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
            validateAssignee(task, request.assignedUserId());
        }

        int sortOrder = subtaskRepository.getNextSortOrder(taskId);

        Subtask subtask = new Subtask(taskId, request.title(), sortOrder);
        subtask.setPageId(request.pageId());
        subtask.setAssignedUserId(request.assignedUserId());
        subtask.setDescription(request.description());
        subtask = subtaskRepository.save(subtask);

        activityService.logSubtaskAdded(taskId, userId, request.title());
        publishQueueChange(task, subtask.getAssignedUserId());

        return toResponse(subtask, task);
    }

    public List<SubtaskDto.Response> createSubtasksWithPages(
            String taskId,
            String userId,
            List<SubtaskDto.CreateWithPageRequest> requests
    ) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        requests.forEach(request -> {
            if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
                validateAssignee(task, request.assignedUserId());
            }
        });

        int nextSortOrder = subtaskRepository.getNextSortOrder(taskId);
        List<Subtask> subtasks = IntStream.range(0, requests.size())
                .mapToObj(index -> {
                    SubtaskDto.CreateWithPageRequest request = requests.get(index);
                    Subtask subtask = new Subtask(taskId, request.title(), nextSortOrder + index);
                    subtask.setPageId(request.pageId());
                    subtask.setAssignedUserId(request.assignedUserId());
                    subtask.setDescription(request.description());
                    return subtask;
                })
                .toList();

        List<Subtask> saved = subtaskRepository.saveAll(subtasks);
        saved.forEach(subtask -> activityService.logSubtaskAdded(taskId, userId, subtask.getTitle()));
        publishQueueChange(task, saved.stream().map(Subtask::getAssignedUserId).toList());
        return toResponses(saved, Map.of(task.getId(), task));
    }

    public SubtaskDto.Response assignSubtask(String taskId, String subtaskId, String userId, SubtaskDto.AssignRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));
        String previousAssignee = subtask.getAssignedUserId();

        if (!subtask.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found for this task");
        }

        // Validate assignee is a task assignee (null means unassign)
        if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
            validateAssignee(task, request.assignedUserId());
            subtask.setAssignedUserId(request.assignedUserId());
        } else {
            subtask.setAssignedUserId(null);
        }

        subtask = subtaskRepository.save(subtask);
        publishQueueChange(task, java.util.Arrays.asList(previousAssignee, subtask.getAssignedUserId()));
        return toResponse(subtask, task);
    }

    public SubtaskDto.BulkResponse bulkUpdateDescription(String taskId, String userId, SubtaskDto.BulkDescriptionRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        List<String> subtaskIds = normalizeIds(request.subtaskIds());
        if (subtaskIds.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        int affected = subtaskRepository.updateDescriptionByTaskIdAndIdIn(taskId, subtaskIds, request.description());
        publishQueueChange(task, task.getAssignedUserIds());
        return new SubtaskDto.BulkResponse(affected);
    }

    public SubtaskDto.BulkResponse bulkAssign(String taskId, String userId, SubtaskDto.BulkAssignRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        // Validate assignee is a task assignee (null means unassign)
        if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
            validateAssignee(task, request.assignedUserId());
        }

        List<String> subtaskIds = normalizeIds(request.subtaskIds());
        if (subtaskIds.isEmpty()) {
            return new SubtaskDto.BulkResponse(0);
        }

        String assignedUserId = (request.assignedUserId() != null && !request.assignedUserId().isBlank())
                ? request.assignedUserId()
                : null;
        List<Subtask> existingSubtasks = subtaskRepository.findByTaskIdAndIdIn(taskId, subtaskIds);
        int affected = subtaskRepository.updateAssignedUserByTaskIdAndIdIn(taskId, subtaskIds, assignedUserId);
        Set<String> affectedUsers = new HashSet<>(task.getAssignedUserIds() == null ? List.of() : task.getAssignedUserIds());
        existingSubtasks.stream().map(Subtask::getAssignedUserId).filter(Objects::nonNull).forEach(affectedUsers::add);
        if (assignedUserId != null) affectedUsers.add(assignedUserId);
        publishQueueChange(task, new ArrayList<>(affectedUsers));

        return new SubtaskDto.BulkResponse(affected);
    }

    public Map<String, Long> getOpenSubtaskCountsForPages(List<String> pageIds, String userId) {
        if (pageIds == null || pageIds.isEmpty()) {
            return Map.of();
        }

        List<Object[]> results = subtaskRepository.countOpenByPageIdsAndAssignedUserId(pageIds, userId);
        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : results) {
            String pageId = (String) row[0];
            Long count = (Long) row[1];
            counts.put(pageId, count);
        }
        return counts;
    }

    public Map<String, List<SubtaskDto.Response>> getOpenSubtasksForPages(List<String> pageIds, String userId) {
        if (pageIds == null || pageIds.isEmpty()) {
            return Map.of();
        }

        List<Subtask> subtasks = subtaskRepository.findOpenByPageIdsAndAssignedUserId(pageIds, userId);
        List<String> taskIds = subtasks.stream().map(Subtask::getTaskId).distinct().toList();
        Map<String, Task> taskMap = taskRepository.findAllById(taskIds).stream()
                .collect(java.util.stream.Collectors.toMap(Task::getId, t -> t));
        Map<String, List<SubtaskDto.Response>> grouped = new HashMap<>();
        List<SubtaskDto.Response> responses = toResponses(subtasks, taskMap);
        Map<String, SubtaskDto.Response> bySubtaskId = responses.stream()
                .collect(java.util.stream.Collectors.toMap(SubtaskDto.Response::id, r -> r));

        for (Subtask subtask : subtasks) {
            if (subtask.getPageId() == null) {
                continue;
            }
            grouped.computeIfAbsent(subtask.getPageId(), k -> new java.util.ArrayList<>())
                    .add(bySubtaskId.get(subtask.getId()));
        }

        return grouped;
    }

    public List<SubtaskDto.Response> getOpenSubtasksForPage(String pageId, String userId) {
        List<Subtask> subtasks = subtaskRepository.findOpenByPageIdAndAssignedUserId(pageId, userId);
        List<String> taskIds = subtasks.stream().map(Subtask::getTaskId).distinct().toList();
        Map<String, Task> taskMap = taskRepository.findAllById(taskIds).stream()
                .collect(java.util.stream.Collectors.toMap(Task::getId, t -> t));
        return toResponses(subtasks, taskMap);
    }

    private void validateAssignee(Task task, String assigneeUserId) {
        boolean isCreator = task.getCreatedByUserId().equals(assigneeUserId);
        boolean isAssignee = task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(assigneeUserId);

        if (!isCreator && !isAssignee) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User must be assigned to the task to be assigned to a subtask");
        }
    }

    private Task verifyTaskAccessAndGet(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        boolean hasAccess = task.getCreatedByUserId().equals(userId) ||
                (task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(userId));

        if (!hasAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this task");
        }

        return task;
    }

    private void verifyTaskMutationAccess(String taskId, String userId) {
        verifyTaskMutationAccessAndGet(taskId, userId);
    }

    private Task verifyTaskCompletionAccess(String taskId, String userId, List<Subtask> subtasks, int requestedCount) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (authorizationPolicyService.canManageTasks(task.getWorkspaceId(), userId)) {
            return task;
        }

        boolean assignedEditor = workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)
                && (task.getStatus() == Task.TaskStatus.OPEN || task.getStatus() == Task.TaskStatus.IN_PROGRESS)
                && task.getAssignedUserIds() != null
                && task.getAssignedUserIds().contains(userId)
                && subtasks.size() == requestedCount
                && subtasks.stream().allMatch(subtask -> subtask.getPageId() != null)
                && subtasks.stream().allMatch(subtask -> userId.equals(subtask.getAssignedUserId()));
        if (!assignedEditor) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only complete your own active assigned subtasks");
        }

        return task;
    }

    private Task verifyTaskMutationAccessAndGet(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!authorizationPolicyService.canManageTasks(task.getWorkspaceId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task management access required");
        }

        return task;
    }

    private SubtaskDto.Response toResponse(Subtask subtask) {
        Task task = taskRepository.findById(subtask.getTaskId()).orElse(null);
        return toResponses(List.of(subtask), task != null ? Map.of(task.getId(), task) : Map.of())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private SubtaskDto.Response toResponse(Subtask subtask, Task task) {
        Map<String, Task> taskMap = task != null ? Map.of(task.getId(), task) : Map.of();
        return toResponses(List.of(subtask), taskMap).stream().findFirst().orElse(null);
    }

    private List<SubtaskDto.Response> toResponses(List<Subtask> subtasks, Map<String, Task> tasksById) {
        if (subtasks == null || subtasks.isEmpty()) {
            return List.of();
        }

        Set<String> userIds = new HashSet<>();
        Set<String> pageIds = new HashSet<>();
        for (Subtask subtask : subtasks) {
            if (subtask.getCompletedByUserId() != null) {
                userIds.add(subtask.getCompletedByUserId());
            }
            if (subtask.getAssignedUserId() != null) {
                userIds.add(subtask.getAssignedUserId());
            }
            if (subtask.getPageId() != null) {
                pageIds.add(subtask.getPageId());
            }
        }

        Map<String, UserProfileDto> usersById = toUserProfiles(userService.getUsersByIds(new ArrayList<>(userIds)));
        Map<String, Page> pagesById = pageIds.isEmpty()
                ? Map.of()
                : pageRepository.findAllByIdIn(pageIds).stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(Page::getId, p -> p));

        List<SubtaskDto.Response> responses = new ArrayList<>(subtasks.size());
        for (Subtask subtask : subtasks) {
            Page page = subtask.getPageId() != null ? pagesById.get(subtask.getPageId()) : null;
            String pageName = page != null ? page.getName() : null;
            String projectId = page != null && page.getProject() != null ? page.getProject().getId() : null;
            String projectName = page != null && page.getProject() != null ? page.getProject().getName() : null;
            Task task = tasksById.get(subtask.getTaskId());

            responses.add(new SubtaskDto.Response(
                    subtask.getId(),
                    subtask.getTaskId(),
                    subtask.getTitle(),
                    subtask.getDescription(),
                    task != null ? task.getDescription() : null,
                    subtask.isCompleted(),
                    subtask.getSortOrder(),
                    subtask.getCompletedAt(),
                    subtask.getCompletedByUserId(),
                    subtask.getCompletedByUserId() != null ? usersById.get(subtask.getCompletedByUserId()) : null,
                    subtask.getCreated(),
                    subtask.getPageId(),
                    pageName,
                    projectId,
                    projectName,
                    subtask.getAssignedUserId(),
                    subtask.getAssignedUserId() != null ? usersById.get(subtask.getAssignedUserId()) : null
            ));
        }
        return responses;
    }

    private Map<String, UserProfileDto> toUserProfiles(Map<String, UserDto> usersById) {
        Map<String, UserProfileDto> profiles = new HashMap<>();
        for (Map.Entry<String, UserDto> entry : usersById.entrySet()) {
            UserDto user = entry.getValue();
            profiles.put(entry.getKey(), new UserProfileDto(
                    user.id(),
                    user.username(),
                    user.email(),
                    user.firstName(),
                    user.lastName(),
                    user.avatar()
            ));
        }
        return profiles;
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private void publishQueueChange(Task task, String userId) {
        if (task != null && userId != null) {
            taskQueueRealtimePublisher.publishAfterCommit(List.of(userId), task.getWorkspaceId());
        }
    }

    private void publishQueueChange(Task task, List<String> userIds) {
        if (task != null) {
            taskQueueRealtimePublisher.publishAfterCommit(userIds, task.getWorkspaceId());
        }
    }
}
