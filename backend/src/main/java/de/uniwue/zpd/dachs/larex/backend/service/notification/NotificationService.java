package de.uniwue.zpd.dachs.larex.backend.service.notification;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.repository.notification.NotificationRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final EmailService emailService;
    private final NotificationBridgeClient notificationBridgeClient;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceService preferenceService,
                               @Lazy EmailService emailService,
                               NotificationBridgeClient notificationBridgeClient) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.emailService = emailService;
        this.notificationBridgeClient = notificationBridgeClient;
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
        return notificationRepository.markAsReadByIdAndUserId(notificationId, userId, LocalDateTime.now()) > 0;
    }

    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
    }

    public Notification createNotification(String userId, String title, String message, Notification.NotificationType type) {
        if (!preferenceService.isInAppEnabledForType(userId, type)) {
            Notification tempNotification = new Notification(userId, title, message, type);
            emailService.sendNotificationEmailIfEnabled(userId, tempNotification);
            return null;
        }

        Notification notification = new Notification(userId, title, message, type);
        Notification saved = notificationRepository.save(notification);
        dispatchSavedNotification(saved);
        return saved;
    }

    public Notification createNotification(String userId, String title, String message, Notification.NotificationType type, String relatedEntityId, String relatedEntityType) {
        return createNotification(userId, title, message, type, relatedEntityId, relatedEntityType, null);
    }

    public Notification createNotification(String userId, String title, String message, Notification.NotificationType type, String relatedEntityId, String relatedEntityType, String link) {
        if (!preferenceService.isInAppEnabledForType(userId, type)) {
            Notification tempNotification = new Notification(userId, title, message, type, relatedEntityId, relatedEntityType, link);
            emailService.sendNotificationEmailIfEnabled(userId, tempNotification);
            return null;
        }

        Notification notification = new Notification(userId, title, message, type, relatedEntityId, relatedEntityType, link);
        Notification saved = notificationRepository.save(notification);
        dispatchSavedNotification(saved);
        return saved;
    }

    private void dispatchSavedNotification(Notification saved) {
        Runnable dispatch = () -> {
            emailService.sendNotificationEmailIfEnabled(saved.getUserId(), saved);
            notificationBridgeClient.pushNotification(saved, "notification-service");
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch.run();
                }
            });
            return;
        }

        dispatch.run();
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

    public void createCollaborationTakeoverRequestedNotification(
            String userId,
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String requesterDisplayName
    ) {
        createNotification(
                userId,
                "Edit access requested",
                requesterDisplayName + " requested edit access for " + formatPageLabel(projectName, pageName) + ".",
                Notification.NotificationType.COLLAB_TAKEOVER_REQUESTED,
                pageId,
                "Page",
                buildEditorLink(projectId, pageId)
        );
    }

    public void createCollaborationTakeoverGrantedNotification(
            String userId,
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String editorDisplayName
    ) {
        createNotification(
                userId,
                "Edit access granted",
                editorDisplayName + " granted you edit access for " + formatPageLabel(projectName, pageName) + ".",
                Notification.NotificationType.COLLAB_TAKEOVER_GRANTED,
                pageId,
                "Page",
                buildEditorLink(projectId, pageId)
        );
    }

    public void createCollaborationTakeoverDeclinedNotification(
            String userId,
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String editorDisplayName
    ) {
        createNotification(
                userId,
                "Edit access declined",
                editorDisplayName + " declined your edit request for " + formatPageLabel(projectName, pageName) + ".",
                Notification.NotificationType.COLLAB_TAKEOVER_DECLINED,
                pageId,
                "Page",
                buildEditorLink(projectId, pageId)
        );
    }

    public void createCollaborationTakeoverForcedNotification(
            String userId,
            String projectId,
            String projectName,
            String pageId,
            String pageName,
            String newEditorDisplayName
    ) {
        createNotification(
                userId,
                "Edit lock taken over",
                newEditorDisplayName + " forcibly took over editing for " + formatPageLabel(projectName, pageName) + ".",
                Notification.NotificationType.COLLAB_TAKEOVER_FORCED,
                pageId,
                "Page",
                buildEditorLink(projectId, pageId)
        );
    }

    public void createCollaborationLeaseExpiredNotification(
            String userId,
            String projectId,
            String projectName,
            String pageId,
            String pageName
    ) {
        createNotification(
                userId,
                "Edit lock expired",
                "Your edit lock for " + formatPageLabel(projectName, pageName) + " expired after the collaboration heartbeat stopped.",
                Notification.NotificationType.COLLAB_LEASE_EXPIRED,
                pageId,
                "Page",
                buildEditorLink(projectId, pageId)
        );
    }

    private String buildEditorLink(String projectId, String pageId) {
        return "/editor?projectId=" + projectId + "&pageId=" + pageId;
    }

    private String formatPageLabel(String projectName, String pageName) {
        return "\"" + projectName + " / " + pageName + "\"";
    }
}
