package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.repository.NotificationRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final EmailService emailService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceService preferenceService,
                               @Lazy EmailService emailService) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.emailService = emailService;
    }

    public List<Notification> getUserNotifications(String userId) {
        return notificationRepository.findByUserIdOrderByCreatedDesc(userId);
    }

    public List<Notification> getUnreadNotifications(String userId) {
        return notificationRepository.findByUserIdAndReadOrderByCreatedDesc(userId, false);
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    public boolean markAsRead(String notificationId, String userId) {
        Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
        if (notificationOpt.isPresent() && notificationOpt.get().getUserId().equals(userId)) {
            Notification notification = notificationOpt.get();
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);
            return true;
        }
        return false;
    }

    public void markAllAsRead(String userId) {
        List<Notification> unreadNotifications = getUnreadNotifications(userId);
        LocalDateTime now = LocalDateTime.now();

        for (Notification notification : unreadNotifications) {
            notification.setRead(true);
            notification.setReadAt(now);
        }
        notificationRepository.saveAll(unreadNotifications);
    }

    public Notification createNotification(String userId, String title, String message, Notification.NotificationType type) {
        // Check if in-app notifications are enabled for this type
        if (!preferenceService.isInAppEnabledForType(userId, type)) {
            // Still might want to send email even if in-app is disabled
            Notification tempNotification = new Notification(userId, title, message, type);
            emailService.sendNotificationEmailIfEnabled(userId, tempNotification);
            return null;
        }
        
        Notification notification = new Notification(userId, title, message, type);
        Notification saved = notificationRepository.save(notification);
        
        // Send email notification if enabled
        emailService.sendNotificationEmailIfEnabled(userId, saved);
        
        return saved;
    }

    public Notification createNotification(String userId, String title, String message, Notification.NotificationType type, String relatedEntityId, String relatedEntityType) {
        // Check if in-app notifications are enabled for this type
        if (!preferenceService.isInAppEnabledForType(userId, type)) {
            // Still might want to send email even if in-app is disabled
            Notification tempNotification = new Notification(userId, title, message, type, relatedEntityId, relatedEntityType);
            emailService.sendNotificationEmailIfEnabled(userId, tempNotification);
            return null;
        }
        
        Notification notification = new Notification(userId, title, message, type, relatedEntityId, relatedEntityType);
        Notification saved = notificationRepository.save(notification);
        
        // Send email notification if enabled
        emailService.sendNotificationEmailIfEnabled(userId, saved);
        
        return saved;
    }

    public void createWorkspaceInvitationNotification(String userId, String workspaceName, String workspaceId) {
        createNotification(
                userId,
                "Workspace Invitation",
                "You have been invited to join the workspace: " + workspaceName,
                Notification.NotificationType.WORKSPACE_INVITATION,
                workspaceId,
                "Workspace"
        );
    }

    public void createTaskAssignedNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "Task Assigned",
                "You have been assigned to task: " + taskTitle,
                Notification.NotificationType.TASK_ASSIGNED,
                taskId,
                "Task"
        );
    }

    public void createTaskCompletedNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "Task Completed",
                "Task has been completed: " + taskTitle,
                Notification.NotificationType.TASK_COMPLETED,
                taskId,
                "Task"
        );
    }

    public void createTaskMentionedNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "Mentioned in Task",
                "You were mentioned in task: " + taskTitle,
                Notification.NotificationType.TASK_MENTIONED,
                taskId,
                "Task"
        );
    }

    public void createTaskCommentAddedNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "New Comment",
                "New comment on task: " + taskTitle,
                Notification.NotificationType.TASK_COMMENT_ADDED,
                taskId,
                "Task"
        );
    }

    public void createTaskDueSoonNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "Task Due Soon",
                "Task is due within 24 hours: " + taskTitle,
                Notification.NotificationType.TASK_DUE_SOON,
                taskId,
                "Task"
        );
    }

    public void createTaskOverdueNotification(String userId, String taskTitle, String taskId) {
        createNotification(
                userId,
                "Task Overdue",
                "Task is overdue: " + taskTitle,
                Notification.NotificationType.TASK_OVERDUE,
                taskId,
                "Task"
        );
    }

    public void createProjectCreatedNotification(String userId, String projectName, String projectId) {
        createNotification(
                userId,
                "New Project Created",
                "A new project has been created: " + projectName,
                Notification.NotificationType.PROJECT_CREATED,
                projectId,
                "Project"
        );
    }

    public void createProjectDeletedNotification(String userId, String projectName, String projectId) {
        createNotification(
                userId,
                "Project Deleted",
                "Project has been deleted: " + projectName,
                Notification.NotificationType.PROJECT_DELETED,
                projectId,
                "Project"
        );
    }

    public void createPageCreatedNotification(String userId, String pageName, String pageId) {
        createNotification(
                userId,
                "New Page Created",
                "A new page has been created: " + pageName,
                Notification.NotificationType.PAGE_CREATED,
                pageId,
                "Page"
        );
    }

    public void createPageDeletedNotification(String userId, String pageName, String pageId) {
        createNotification(
                userId,
                "Page Deleted",
                "Page has been deleted: " + pageName,
                Notification.NotificationType.PAGE_DELETED,
                pageId,
                "Page"
        );
    }

    public void createBatchPageDeletedNotification(String userId, List<String> pageNames, String projectId, String projectName) {
        int count = pageNames.size();
        String title = count + " Pages Deleted";
        String message = count + " pages deleted from project: \"" + projectName + "\"";
        createNotification(userId, title, message, Notification.NotificationType.PAGE_DELETED, projectId, "Project");
    }

    public void deleteAllRead(String userId) {
        notificationRepository.deleteByUserIdAndRead(userId, true);
    }

    public void createUploadCompletedNotification(String userId, String projectName, String projectId,
                                                   int totalFiles, int failedFiles) {
        String title = "Upload Completed";
        String message;
        if (failedFiles > 0) {
            message = String.format("Upload to project \"%s\" completed: %d files processed, %d failed",
                    projectName, totalFiles - failedFiles, failedFiles);
        } else {
            message = String.format("Upload to project \"%s\" completed: %d files processed successfully",
                    projectName, totalFiles);
        }
        createNotification(userId, title, message,
                Notification.NotificationType.UPLOAD_COMPLETED, projectId, "Project");
    }

    public void createUploadFailedNotification(String userId, String projectName, String projectId, String errorMessage) {
        createNotification(
                userId,
                "Upload Failed",
                "Upload to project \"" + projectName + "\" failed: " + errorMessage,
                Notification.NotificationType.UPLOAD_FAILED,
                projectId,
                "Project"
        );
    }

    public void createUploadConflictsNotification(String userId, String projectName, String projectId,
                                                   int totalFiles, int processedFiles, int conflictFiles) {
        String message = String.format(
                "Upload to project \"%s\" completed with conflicts: %d files processed, %d conflicts require resolution",
                projectName, processedFiles, conflictFiles);
        createNotification(userId, "Upload Has Conflicts", message,
                Notification.NotificationType.UPLOAD_COMPLETED, projectId, "Project");
    }

    public void createImportCompletedNotification(String userId, String projectName, String projectId,
                                                   int totalFiles, int failedFiles) {
        String title = "Import Completed";
        String message;
        if (failedFiles > 0) {
            message = String.format("Import to project \"%s\" completed: %d files imported, %d failed",
                    projectName, totalFiles - failedFiles, failedFiles);
        } else {
            message = String.format("Import to project \"%s\" completed: %d files imported successfully",
                    projectName, totalFiles);
        }
        createNotification(userId, title, message,
                Notification.NotificationType.IMPORT_COMPLETED, projectId, "Project");
    }

    public void createImportFailedNotification(String userId, String projectName, String projectId, String errorMessage) {
        createNotification(
                userId,
                "Import Failed",
                "Import to project \"" + projectName + "\" failed: " + errorMessage,
                Notification.NotificationType.IMPORT_FAILED,
                projectId,
                "Project"
        );
    }
}
