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
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class BulkTaskService {

    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final TaskActivityService activityService;

    public BulkTaskService(
            TaskRepository taskRepository,
            WorkspaceAccessService workspaceAccessService,
            TaskActivityService activityService
    ) {
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.activityService = activityService;
    }

    public BulkTaskDto.BulkOperationResponse bulkUpdateStatus(
            String workspaceId,
            String userId,
            BulkTaskDto.BulkStatusRequest request
    ) {
        verifyWorkspaceAccess(workspaceId, userId);

        int successCount = 0;
        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, request.taskIds(), failedTaskIds, errors);

        for (String taskId : request.taskIds()) {
            try {
                Task task = taskById.get(taskId);
                if (task == null) {
                    continue;
                }

                Task.TaskStatus oldStatus = task.getStatus();
                task.setStatus(request.status());

                if (request.status() == Task.TaskStatus.COMPLETED && task.getCompletedAt() == null) {
                    task.setCompletedAt(LocalDateTime.now());
                    task.setCompletedByUserId(userId);
                } else if (request.status() != Task.TaskStatus.COMPLETED) {
                    task.setCompletedAt(null);
                    task.setCompletedByUserId(null);
                }

                taskRepository.save(task);
                activityService.logStatusChanged(taskId, userId, oldStatus, request.status());
                successCount++;
            } catch (Exception e) {
                failedTaskIds.add(taskId);
                errors.add("Error updating task " + taskId + ": " + e.getMessage());
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

        int successCount = 0;
        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, request.taskIds(), failedTaskIds, errors);

        for (String taskId : request.taskIds()) {
            try {
                Task task = taskById.get(taskId);
                if (task == null) {
                    continue;
                }

                Task.TaskPriority oldPriority = task.getPriority();
                task.setPriority(request.priority());
                taskRepository.save(task);
                activityService.logPriorityChanged(taskId, userId, oldPriority, request.priority());
                successCount++;
            } catch (Exception e) {
                failedTaskIds.add(taskId);
                errors.add("Error updating task " + taskId + ": " + e.getMessage());
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

        int successCount = 0;
        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Set<String> usersToAdd = request.addUserIds() != null ? new HashSet<>(request.addUserIds()) : new HashSet<>();
        Set<String> usersToRemove = request.removeUserIds() != null ? new HashSet<>(request.removeUserIds()) : new HashSet<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, request.taskIds(), failedTaskIds, errors);

        for (String taskId : request.taskIds()) {
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

                task.setAssignedUserIds(currentAssignees);
                taskRepository.save(task);

                if (!addedUsers.isEmpty() || !removedUsers.isEmpty()) {
                    activityService.logAssigneesChanged(taskId, userId, addedUsers, removedUsers);
                }

                successCount++;
            } catch (Exception e) {
                failedTaskIds.add(taskId);
                errors.add("Error updating task " + taskId + ": " + e.getMessage());
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

        int successCount = 0;
        List<String> failedTaskIds = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Task> taskById = preloadTasksInWorkspace(workspaceId, request.taskIds(), failedTaskIds, errors);

        for (String taskId : request.taskIds()) {
            try {
                Task task = taskById.get(taskId);
                if (task == null) {
                    continue;
                }

                taskRepository.delete(task);
                successCount++;
            } catch (Exception e) {
                failedTaskIds.add(taskId);
                errors.add("Error deleting task " + taskId + ": " + e.getMessage());
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
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void verifyWorkspaceAdmin(String workspaceId, String userId) {
        if (!workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required for this operation");
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
}
