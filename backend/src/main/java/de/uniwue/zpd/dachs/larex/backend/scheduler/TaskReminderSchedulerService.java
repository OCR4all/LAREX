package de.uniwue.zpd.dachs.larex.backend.scheduler;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskReminder;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskReminderRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional
public class TaskReminderSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(TaskReminderSchedulerService.class);

    private final TaskRepository taskRepository;
    private final TaskReminderRepository reminderRepository;
    private final NotificationService notificationService;

    // Track notified tasks to avoid duplicate notifications
    private final Set<String> dueSoonNotifiedTasks = new HashSet<>();
    private final Set<String> overdueNotifiedTasks = new HashSet<>();

    public TaskReminderSchedulerService(
            TaskRepository taskRepository,
            TaskReminderRepository reminderRepository,
            NotificationService notificationService
    ) {
        this.taskRepository = taskRepository;
        this.reminderRepository = reminderRepository;
        this.notificationService = notificationService;
    }

    /**
     * Check for tasks due within 24 hours and send notifications.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void checkDueSoonTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in24Hours = now.plusHours(24);

        List<Task> dueSoonTasks = taskRepository.findTasksDueBetween(now, in24Hours);

        for (Task task : dueSoonTasks) {
            String taskKey = task.getId() + "-" + task.getDueDate().toLocalDate();

            if (dueSoonNotifiedTasks.contains(taskKey)) {
                continue; // Already notified today
            }

            // Notify all assignees
            if (task.getAssignedUserIds() != null) {
                for (String assigneeId : task.getAssignedUserIds()) {
                    notificationService.createTaskDueSoonNotification(assigneeId, task.getTitle(), task.getId());
                }
            }

            dueSoonNotifiedTasks.add(taskKey);
            logger.info("Sent due soon notification for task: {}", task.getId());
        }

        // Clean up old entries from the set (older than 2 days)
        cleanupNotifiedSet(dueSoonNotifiedTasks);
    }

    /**
     * Check for overdue tasks and send notifications.
     * Runs daily at 8 AM.
     */
    @Scheduled(cron = "0 0 8 * * *")
    public void checkOverdueTasks() {
        LocalDateTime now = LocalDateTime.now();

        List<Task> overdueTasks = taskRepository.findOverdueTasks(now);

        for (Task task : overdueTasks) {
            String taskKey = task.getId() + "-" + now.toLocalDate();

            if (overdueNotifiedTasks.contains(taskKey)) {
                continue; // Already notified today
            }

            // Notify all assignees
            if (task.getAssignedUserIds() != null) {
                for (String assigneeId : task.getAssignedUserIds()) {
                    notificationService.createTaskOverdueNotification(assigneeId, task.getTitle(), task.getId());
                }
            }

            overdueNotifiedTasks.add(taskKey);
            logger.info("Sent overdue notification for task: {}", task.getId());
        }

        // Clean up old entries
        cleanupNotifiedSet(overdueNotifiedTasks);
    }

    /**
     * Process user-defined reminders.
     * Runs every minute.
     */
    @Scheduled(fixedRate = 60000) // 1 minute
    public void processUserReminders() {
        LocalDateTime now = LocalDateTime.now();

        List<TaskReminder> pendingReminders = reminderRepository.findBySentFalseAndReminderTimeBefore(now);

        for (TaskReminder reminder : pendingReminders) {
            Task task = taskRepository.findById(reminder.getTaskId()).orElse(null);

            if (task != null && task.getStatus() != Task.TaskStatus.COMPLETED && task.getStatus() != Task.TaskStatus.CANCELLED) {
                notificationService.createNotification(
                        reminder.getUserId(),
                        "Task Reminder",
                        "Reminder for task: " + task.getTitle(),
                        de.uniwue.zpd.dachs.larex.backend.entity.Notification.NotificationType.TASK_REMINDER,
                        task.getId(),
                        "Task"
                );
                logger.info("Sent user reminder for task: {} to user: {}", task.getId(), reminder.getUserId());
            }

            reminder.setSent(true);
            reminderRepository.save(reminder);
        }
    }

    private void cleanupNotifiedSet(Set<String> set) {
        // Simple cleanup: if set gets too large, clear it
        // In production, you might want a more sophisticated approach
        if (set.size() > 10000) {
            set.clear();
        }
    }
}
