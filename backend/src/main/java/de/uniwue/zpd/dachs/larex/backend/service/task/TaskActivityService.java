package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskActivityLogDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskActivityLog;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskActivityLogRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskActivityService {

    private final TaskActivityLogRepository activityLogRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UserService userService;

    public TaskActivityService(
            TaskActivityLogRepository activityLogRepository,
            TaskRepository taskRepository,
            WorkspaceAccessService workspaceAccessService,
            UserService userService
    ) {
        this.activityLogRepository = activityLogRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.userService = userService;
    }

    public List<TaskActivityLogDto.Response> getTaskActivity(String taskId, String userId, int page, int size) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!workspaceAccessService.hasWorkspaceAccess(task.getWorkspaceId(), userId)) {
            throw new SecurityException("Access denied.");
        }

        Page<TaskActivityLog> activityPage = activityLogRepository.findByTaskIdOrderByCreatedDesc(
                taskId, PageRequest.of(page, size)
        );

        return mapActivityLogs(activityPage.getContent());
    }

    public void logTaskCreated(String taskId, String userId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.CREATED);
        activityLogRepository.save(log);
    }

    public void logStatusChanged(String taskId, String userId, Task.TaskStatus oldStatus, Task.TaskStatus newStatus) {
        TaskActivityLog log = new TaskActivityLog(
                taskId, userId, TaskActivityLog.ActivityType.STATUS_CHANGED,
                oldStatus.name(), newStatus.name()
        );
        activityLogRepository.save(log);
    }

    public void logTitleChanged(String taskId, String userId, String oldTitle, String newTitle) {
        TaskActivityLog log = new TaskActivityLog(
                taskId, userId, TaskActivityLog.ActivityType.TITLE_CHANGED,
                oldTitle, newTitle
        );
        activityLogRepository.save(log);
    }

    public void logDescriptionChanged(String taskId, String userId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.DESCRIPTION_CHANGED);
        activityLogRepository.save(log);
    }

    public void logPriorityChanged(String taskId, String userId, Task.TaskPriority oldPriority, Task.TaskPriority newPriority) {
        TaskActivityLog log = new TaskActivityLog(
                taskId, userId, TaskActivityLog.ActivityType.PRIORITY_CHANGED,
                oldPriority.name(), newPriority.name()
        );
        activityLogRepository.save(log);
    }

    public void logDueDateChanged(String taskId, String userId, String oldDueDate, String newDueDate) {
        TaskActivityLog log = new TaskActivityLog(
                taskId, userId, TaskActivityLog.ActivityType.DUE_DATE_CHANGED,
                oldDueDate, newDueDate
        );
        activityLogRepository.save(log);
    }

    public void logAssigneesChanged(String taskId, String userId, List<String> added, List<String> removed) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.ASSIGNEES_CHANGED);
        String details = String.format("{\"added\":%s,\"removed\":%s}",
                toJsonArray(added), toJsonArray(removed));
        log.setDetails(details);
        activityLogRepository.save(log);
    }

    public void logCommentAdded(String taskId, String userId, String commentId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.COMMENT_ADDED);
        log.setDetails(commentId);
        activityLogRepository.save(log);
    }

    public void logCommentUpdated(String taskId, String userId, String commentId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.COMMENT_UPDATED);
        log.setDetails(commentId);
        activityLogRepository.save(log);
    }

    public void logCommentDeleted(String taskId, String userId, String commentId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.COMMENT_DELETED);
        log.setDetails(commentId);
        activityLogRepository.save(log);
    }

    public void logLinkAdded(String taskId, String userId, String linkType, String linkId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.LINK_ADDED);
        log.setDetails(String.format("{\"type\":\"%s\",\"id\":\"%s\"}", linkType, linkId));
        activityLogRepository.save(log);
    }

    public void logLinkRemoved(String taskId, String userId, String linkType, String linkId) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.LINK_REMOVED);
        log.setDetails(String.format("{\"type\":\"%s\",\"id\":\"%s\"}", linkType, linkId));
        activityLogRepository.save(log);
    }

    public void logSubtaskAdded(String taskId, String userId, String subtaskTitle) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.SUBTASK_ADDED);
        log.setDetails(subtaskTitle);
        activityLogRepository.save(log);
    }

    public void logSubtaskCompleted(String taskId, String userId, String subtaskTitle) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.SUBTASK_COMPLETED);
        log.setDetails(subtaskTitle);
        activityLogRepository.save(log);
    }

    public void logSubtaskDeleted(String taskId, String userId, String subtaskTitle) {
        TaskActivityLog log = new TaskActivityLog(taskId, userId, TaskActivityLog.ActivityType.SUBTASK_DELETED);
        log.setDetails(subtaskTitle);
        activityLogRepository.save(log);
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        return "[" + items.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",")) + "]";
    }

    private List<TaskActivityLogDto.Response> mapActivityLogs(List<TaskActivityLog> logs) {
        if (logs.isEmpty()) return List.of();

        Set<String> userIds = logs.stream()
                .map(TaskActivityLog::getUserId)
                .collect(Collectors.toSet());

        Map<String, UserDto> users = userService.getUsersByIds(new ArrayList<>(userIds));

        return logs.stream().map(log -> new TaskActivityLogDto.Response(
                log.getId(),
                log.getTaskId(),
                log.getUserId(),
                users.get(log.getUserId()),
                log.getActivityType(),
                log.getOldValue(),
                log.getNewValue(),
                log.getDetails(),
                log.getCreated()
        )).toList();
    }
}
