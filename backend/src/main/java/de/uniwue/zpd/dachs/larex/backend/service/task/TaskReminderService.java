package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskReminderDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskReminder;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskReminderRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class TaskReminderService {

    private final TaskReminderRepository reminderRepository;
    private final TaskRepository taskRepository;
    private final AuthorizationPolicyService authorizationPolicyService;

    public TaskReminderService(TaskReminderRepository reminderRepository,
                               TaskRepository taskRepository,
                               AuthorizationPolicyService authorizationPolicyService) {
        this.reminderRepository = reminderRepository;
        this.taskRepository = taskRepository;
        this.authorizationPolicyService = authorizationPolicyService;
    }

    public List<TaskReminderDto.Response> getTaskReminders(String taskId, String userId) {
        verifyTaskAccess(taskId, userId);

        return reminderRepository.findByTaskIdAndUserId(taskId, userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TaskReminderDto.Response createReminder(String taskId, String userId, TaskReminderDto.CreateRequest request) {
        verifyTaskMutationAccess(taskId, userId);

        if (request.reminderTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reminder time must be in the future");
        }

        TaskReminder reminder = new TaskReminder(taskId, userId, request.reminderTime());
        reminder = reminderRepository.save(reminder);

        return toResponse(reminder);
    }

    public void deleteReminder(String taskId, String reminderId, String userId) {
        verifyTaskMutationAccess(taskId, userId);

        TaskReminder reminder = reminderRepository.findById(reminderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found"));

        if (!reminder.getTaskId().equals(taskId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reminder not found for this task");
        }

        if (!reminder.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete another user's reminder");
        }

        reminderRepository.delete(reminder);
    }

    private void verifyTaskAccess(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        // User must be assigned to the task or be the creator
        boolean hasAccess = task.getCreatedByUserId().equals(userId) ||
                (task.getAssignedUserIds() != null && task.getAssignedUserIds().contains(userId));

        if (!hasAccess) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this task");
        }
    }

    private void verifyTaskMutationAccess(String taskId, String userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));

        if (!authorizationPolicyService.canManageTasks(task.getWorkspaceId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task management access required");
        }
    }

    private TaskReminderDto.Response toResponse(TaskReminder reminder) {
        return new TaskReminderDto.Response(
                reminder.getId(),
                reminder.getTaskId(),
                reminder.getUserId(),
                reminder.getReminderTime(),
                reminder.isSent(),
                reminder.getCreated()
        );
    }
}
