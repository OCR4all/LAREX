package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    public SubtaskService(
            SubtaskRepository subtaskRepository,
            TaskRepository taskRepository,
            PageRepository pageRepository,
            TaskActivityService activityService,
            UserService userService,
            AuthorizationPolicyService authorizationPolicyService
    ) {
        this.subtaskRepository = subtaskRepository;
        this.taskRepository = taskRepository;
        this.pageRepository = pageRepository;
        this.activityService = activityService;
        this.userService = userService;
        this.authorizationPolicyService = authorizationPolicyService;
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
        return toResponse(subtask, task);
    }

    public void reorderSubtasks(String taskId, String userId, SubtaskDto.ReorderRequest request) {
        verifyTaskMutationAccess(taskId, userId);

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
            subtaskRepository.save(subtask);
        });
    }

    public void deleteSubtask(String taskId, String subtaskId, String userId) {
        verifyTaskMutationAccess(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));

        if (!subtask.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found for this task");
        }

        String title = subtask.getTitle();
        subtaskRepository.delete(subtask);

        activityService.logSubtaskDeleted(taskId, userId, title);
    }

    public SubtaskDto.BulkResponse bulkComplete(String taskId, String userId, SubtaskDto.BulkRequest request) {
        verifyTaskMutationAccess(taskId, userId);

        List<Subtask> subtasks = subtaskRepository.findAllById(request.subtaskIds());
        int affected = 0;

        for (Subtask subtask : subtasks) {
            if (!subtask.getTaskId().equals(taskId)) {
                continue;
            }
            if (!subtask.isCompleted()) {
                subtask.setCompleted(true);
                subtask.setCompletedAt(LocalDateTime.now());
                subtask.setCompletedByUserId(userId);
                subtaskRepository.save(subtask);
                activityService.logSubtaskCompleted(taskId, userId, subtask.getTitle());
                affected++;
            }
        }

        return new SubtaskDto.BulkResponse(affected);
    }

    public SubtaskDto.BulkResponse bulkDelete(String taskId, String userId, SubtaskDto.BulkRequest request) {
        verifyTaskMutationAccess(taskId, userId);

        List<Subtask> subtasks = subtaskRepository.findAllById(request.subtaskIds());
        int affected = 0;

        for (Subtask subtask : subtasks) {
            if (!subtask.getTaskId().equals(taskId)) {
                continue;
            }
            String title = subtask.getTitle();
            subtaskRepository.delete(subtask);
            activityService.logSubtaskDeleted(taskId, userId, title);
            affected++;
        }

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

        return toResponse(subtask, task);
    }

    public SubtaskDto.Response assignSubtask(String taskId, String subtaskId, String userId, SubtaskDto.AssignRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        Subtask subtask = subtaskRepository.findById(subtaskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subtask not found"));

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
        return toResponse(subtask, task);
    }

    public SubtaskDto.BulkResponse bulkUpdateDescription(String taskId, String userId, SubtaskDto.BulkDescriptionRequest request) {
        verifyTaskMutationAccess(taskId, userId);

        List<Subtask> subtasks = subtaskRepository.findAllById(request.subtaskIds());
        int affected = 0;

        for (Subtask subtask : subtasks) {
            if (!subtask.getTaskId().equals(taskId)) {
                continue;
            }
            subtask.setDescription(request.description());
            subtaskRepository.save(subtask);
            affected++;
        }

        return new SubtaskDto.BulkResponse(affected);
    }

    public SubtaskDto.BulkResponse bulkAssign(String taskId, String userId, SubtaskDto.BulkAssignRequest request) {
        Task task = verifyTaskMutationAccessAndGet(taskId, userId);

        // Validate assignee is a task assignee (null means unassign)
        if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
            validateAssignee(task, request.assignedUserId());
        }

        List<Subtask> subtasks = subtaskRepository.findAllById(request.subtaskIds());
        int affected = 0;

        for (Subtask subtask : subtasks) {
            if (!subtask.getTaskId().equals(taskId)) {
                continue;
            }
            if (request.assignedUserId() != null && !request.assignedUserId().isBlank()) {
                subtask.setAssignedUserId(request.assignedUserId());
            } else {
                subtask.setAssignedUserId(null);
            }
            subtaskRepository.save(subtask);
            affected++;
        }

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
}
