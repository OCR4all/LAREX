package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkTaskDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class BulkTaskService {

    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final TaskActivityService activityService;
    private final TaskStatusTransactionService taskStatusTransactionService;

    public BulkTaskService(
            TaskRepository taskRepository,
            WorkspaceAccessService workspaceAccessService,
            TaskActivityService activityService,
            TaskStatusTransactionService taskStatusTransactionService
    ) {
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.activityService = activityService;
        this.taskStatusTransactionService = taskStatusTransactionService;
    }

    public BulkTaskDto.BulkOperationResponse bulkUpdateStatus(
            String workspaceId,
            String userId,
            BulkTaskDto.BulkStatusRequest request
    ) {
        verifyWorkspaceAccess(workspaceId, userId);

        List<String> taskIds = normalizeIds(request.taskIds());
        if (taskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(0, 0, List.of(), List.of());
        }

        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, taskIds, failedTaskIds, errors);
        List<String> targetTaskIds = taskIds.stream().filter(taskById::containsKey).toList();

        if (targetTaskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(
                    0,
                    failedTaskIds.size(),
                    failedTaskIds,
                    errors
            );
        }

        int successCount = 0;
        for (String taskId : targetTaskIds) {
            try {
                taskStatusTransactionService.updateStatus(taskId, userId, request.status());
                successCount++;
            } catch (RuntimeException e) {
                failedTaskIds.add(taskId);
                errors.add("Task " + taskId + ": " + (e.getMessage() == null ? "status update failed" : e.getMessage()));
            }
        }

        return new BulkTaskDto.BulkOperationResponse(
                successCount,
                failedTaskIds.size(),
                failedTaskIds,
                errors
        );
    }

    public BulkTaskDto.BulkOperationResponse bulkUpdatePriority(
            String workspaceId,
            String userId,
            BulkTaskDto.BulkPriorityRequest request
    ) {
        verifyWorkspaceAccess(workspaceId, userId);

        List<String> taskIds = normalizeIds(request.taskIds());
        if (taskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(0, 0, List.of(), List.of());
        }

        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, taskIds, failedTaskIds, errors);
        List<String> targetTaskIds = taskIds.stream().filter(taskById::containsKey).toList();

        if (targetTaskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(
                    0,
                    failedTaskIds.size(),
                    failedTaskIds,
                    errors
            );
        }

        Map<String, Task.TaskPriority> oldPriorityByTaskId = new HashMap<>();
        for (String taskId : targetTaskIds) {
            oldPriorityByTaskId.put(taskId, taskById.get(taskId).getPriority());
        }

        int successCount;
        try {
            successCount = taskRepository.bulkUpdatePriorityInWorkspace(
                    workspaceId,
                    targetTaskIds,
                    request.priority()
            );
        } catch (Exception e) {
            failedTaskIds.addAll(targetTaskIds);
            errors.add("Error bulk updating task priority: " + e.getMessage());
            return new BulkTaskDto.BulkOperationResponse(
                    0,
                    failedTaskIds.size(),
                    failedTaskIds,
                    errors
            );
        }

        for (String taskId : targetTaskIds) {
            Task.TaskPriority oldPriority = oldPriorityByTaskId.get(taskId);
            if (oldPriority != null) {
                activityService.logPriorityChanged(taskId, userId, oldPriority, request.priority());
            }
        }

        return new BulkTaskDto.BulkOperationResponse(
                successCount,
                failedTaskIds.size(),
                failedTaskIds,
                errors
        );
    }

    public BulkTaskDto.BulkOperationResponse bulkUpdateAssignees(
            String workspaceId,
            String userId,
            BulkTaskDto.BulkAssigneesRequest request
    ) {
        verifyWorkspaceAccess(workspaceId, userId);

        List<String> taskIds = normalizeIds(request.taskIds());
        if (taskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(0, 0, List.of(), List.of());
        }

        int successCount = 0;
        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Set<String> usersToAdd = request.addUserIds() != null ? new HashSet<>(request.addUserIds()) : new HashSet<>();
        Set<String> usersToRemove = request.removeUserIds() != null ? new HashSet<>(request.removeUserIds()) : new HashSet<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, taskIds, failedTaskIds, errors);
        List<Task> tasksToSave = new ArrayList<>();
        List<AssigneeChange> assigneeChanges = new ArrayList<>();

        for (String taskId : taskIds) {
            try {
                Task task = taskById.get(taskId);
                if (task == null) {
                    continue;
                }

                List<String> currentAssignees = task.getAssignedUserIds() != null ?
                        new ArrayList<>(task.getAssignedUserIds()) : new ArrayList<>();

                List<String> addedUsers = new ArrayList<>();
                List<String> removedUsers = new ArrayList<>();

                // Add users
                for (String addUserId : usersToAdd) {
                    if (!currentAssignees.contains(addUserId)) {
                        currentAssignees.add(addUserId);
                        addedUsers.add(addUserId);
                    }
                }

                // Remove users
                for (String removeUserId : usersToRemove) {
                    if (currentAssignees.remove(removeUserId)) {
                        removedUsers.add(removeUserId);
                    }
                }

                if (!addedUsers.isEmpty() || !removedUsers.isEmpty()) {
                    task.setAssignedUserIds(currentAssignees);
                    tasksToSave.add(task);
                    assigneeChanges.add(new AssigneeChange(taskId, addedUsers, removedUsers));
                }

                successCount++;
            } catch (Exception e) {
                failedTaskIds.add(taskId);
                errors.add("Error updating task " + taskId + ": " + e.getMessage());
            }
        }

        if (!tasksToSave.isEmpty()) {
            try {
                taskRepository.saveAll(tasksToSave);
                for (AssigneeChange change : assigneeChanges) {
                    activityService.logAssigneesChanged(
                            change.taskId(),
                            userId,
                            change.addedUsers(),
                            change.removedUsers()
                    );
                }
            } catch (Exception e) {
                Set<String> changedTaskIds = assigneeChanges.stream()
                        .map(AssigneeChange::taskId)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                failedTaskIds.addAll(changedTaskIds);
                errors.add("Error bulk updating task assignees: " + e.getMessage());
                successCount -= changedTaskIds.size();
            }
        }

        return new BulkTaskDto.BulkOperationResponse(
                successCount,
                failedTaskIds.size(),
                failedTaskIds,
                errors
        );
    }

    public BulkTaskDto.BulkOperationResponse bulkDelete(
            String workspaceId,
            String userId,
            BulkTaskDto.BulkDeleteRequest request
    ) {
        verifyWorkspaceAdmin(workspaceId, userId);

        List<String> taskIds = normalizeIds(request.taskIds());
        if (taskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(0, 0, List.of(), List.of());
        }

        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, taskIds, failedTaskIds, errors);
        List<String> targetTaskIds = taskIds.stream().filter(taskById::containsKey).toList();
        if (targetTaskIds.isEmpty()) {
            return new BulkTaskDto.BulkOperationResponse(
                    0,
                    failedTaskIds.size(),
                    failedTaskIds,
                    errors
            );
        }

        int successCount = 0;
        for (String taskId : targetTaskIds) {
            try {
                taskStatusTransactionService.deleteTask(taskId, userId);
                successCount++;
            } catch (RuntimeException e) {
                failedTaskIds.add(taskId);
                errors.add("Task " + taskId + ": " + (e.getMessage() == null ? "delete failed" : e.getMessage()));
            }
        }

        return new BulkTaskDto.BulkOperationResponse(
                successCount,
                failedTaskIds.size(),
                failedTaskIds,
                errors
        );
    }

    private void verifyWorkspaceAccess(String workspaceId, String userId) {
        if (!workspaceAccessService.canManageTasks(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task management access required for this operation");
        }
    }

    private void verifyWorkspaceAdmin(String workspaceId, String userId) {
        if (!workspaceAccessService.canManageTasks(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task management access required for this operation");
        }
    }

    private Map<String, Task> preloadTasksInWorkspace(
            String workspaceId,
            List<String> taskIds,
            List<String> failedTaskIds,
            List<String> errors
    ) {
        Map<String, Task> taskById = new HashMap<>();
        for (Task task : taskRepository.findAllById(taskIds)) {
            taskById.put(task.getId(), task);
        }

        for (String taskId : taskIds) {
            Task task = taskById.get(taskId);
            if (task == null) {
                failedTaskIds.add(taskId);
                errors.add("Task not found: " + taskId);
                continue;
            }
            if (!workspaceId.equals(task.getWorkspaceId())) {
                failedTaskIds.add(taskId);
                errors.add("Task does not belong to workspace: " + taskId);
                taskById.remove(taskId);
            }
        }
        return taskById;
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

    private record AssigneeChange(String taskId, List<String> addedUsers, List<String> removedUsers) {}
}
