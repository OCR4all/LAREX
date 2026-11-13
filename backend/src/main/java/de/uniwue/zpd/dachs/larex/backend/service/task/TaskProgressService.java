package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class TaskProgressService {

    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    private final TaskActivityService activityService;
    private final NotificationService notificationService;
    private final TaskQueueRealtimePublisher taskQueueRealtimePublisher;

    public TaskProgressService(
            TaskRepository taskRepository,
            SubtaskRepository subtaskRepository,
            @Lazy TaskActivityService activityService,
            NotificationService notificationService,
            TaskQueueRealtimePublisher taskQueueRealtimePublisher
    ) {
        this.taskRepository = taskRepository;
        this.subtaskRepository = subtaskRepository;
        this.activityService = activityService;
        this.notificationService = notificationService;
        this.taskQueueRealtimePublisher = taskQueueRealtimePublisher;
    }

    public static Task.TaskStatus deriveStatus(long total, long completed) {
        if (total > 0 && completed >= total) {
            return Task.TaskStatus.COMPLETED;
        }
        return completed > 0 ? Task.TaskStatus.IN_PROGRESS : Task.TaskStatus.OPEN;
    }

    public Task.TaskStatus deriveStatus(String taskId) {
        for (Object[] row : subtaskRepository.countProgressByTaskIds(List.of(taskId))) {
            return deriveStatus(toLong(row[1]), toLong(row[2]));
        }
        return Task.TaskStatus.OPEN;
    }

    public void recompute(String taskId, String actorUserId) {
        recompute(List.of(taskId), actorUserId);
    }

    public void recompute(Collection<String> taskIds, String actorUserId) {
        Set<String> ids = new LinkedHashSet<>();
        if (taskIds != null) {
            taskIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return;
        }

        Map<String, long[]> progressByTaskId = new HashMap<>();
        for (Object[] row : subtaskRepository.countProgressByTaskIds(ids)) {
            progressByTaskId.put((String) row[0], new long[]{toLong(row[1]), toLong(row[2])});
        }

        for (Task task : taskRepository.findAllById(ids)) {
            if (task.isPaused()) {
                continue;
            }
            long[] progress = progressByTaskId.getOrDefault(task.getId(), new long[]{0, 0});
            Task.TaskStatus derived = deriveStatus(progress[0], progress[1]);
            applyDerivedStatus(task, derived, actorUserId);
        }
    }

    private void applyDerivedStatus(Task task, Task.TaskStatus derived, String actorUserId) {
        Task.TaskStatus previous = task.getStatus();
        if (previous == derived) {
            return;
        }

        task.setStatus(derived);
        if (derived == Task.TaskStatus.COMPLETED) {
            task.setCompletedAt(LocalDateTime.now());
            task.setCompletedByUserId(actorUserId);
        } else {
            task.setCompletedAt(null);
            task.setCompletedByUserId(null);
        }
        taskRepository.save(task);
        activityService.logStatusDerived(task.getId(), actorUserId, previous, derived);

        if (derived == Task.TaskStatus.COMPLETED && !task.isArchived()) {
            Set<String> notifyUsers = new LinkedHashSet<>();
            if (task.getAssignedUserIds() != null) {
                notifyUsers.addAll(task.getAssignedUserIds());
            }
            notifyUsers.add(task.getCreatedByUserId());
            notifyUsers.remove(actorUserId);
            notifyUsers.remove(null);
            for (String userId : notifyUsers) {
                notificationService.createTaskCompletedNotification(userId, task.getTitle(), task.getId());
            }
        }

        taskQueueRealtimePublisher.publishAfterCommit(task.getAssignedUserIds(), task.getWorkspaceId());
    }

    private static long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
