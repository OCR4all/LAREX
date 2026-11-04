package de.uniwue.zpd.dachs.larex.backend.service.notification;

import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent.Action;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PageWorkflowPublisher {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotificationService notificationService;
    private final NotificationBridgeClient notificationBridgeClient;
    private final TaskExecutor taskExecutor;

    public PageWorkflowPublisher(
            WorkspaceMemberRepository workspaceMemberRepository,
            NotificationService notificationService,
            NotificationBridgeClient notificationBridgeClient,
            @Qualifier("actionNotificationTaskExecutor") TaskExecutor taskExecutor
    ) {
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.notificationService = notificationService;
        this.notificationBridgeClient = notificationBridgeClient;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Announces one workflow action. Notifications are per action and recipient, never per page,
     * because a single action can cover hundreds of pages.
     */
    public void publish(Project project,
                        List<Page> pages,
                        Action action,
                        String actorUserId,
                        String comment,
                        Collection<String> changeRecipients) {
        if (pages.isEmpty()) {
            return;
        }
        String workspaceId = project.getLibrary().getWorkspaceId();
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .filter(member -> member.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED)
                .toList();

        List<Page> inReview = pages.stream()
                .filter(page -> page.getWorkflowState() == Page.WorkflowState.IN_REVIEW)
                .toList();
        if (!inReview.isEmpty()) {
            notifyCurators(project, members, inReview.size(), actorUserId);
        }
        if (action == Action.REQUEST_CHANGES) {
            notifyChangesRequested(project, pages.size(), actorUserId, comment, changeRecipients);
        }
        pushAfterCommit(members.stream().map(WorkspaceMember::getUserId).toList(), workspaceId, project.getId(),
                pages.stream().map(Page::getId).toList());
    }

    private void notifyCurators(Project project, List<WorkspaceMember> members, int pageCount, String actorUserId) {
        for (WorkspaceMember member : members) {
            if (member.getRole().isCuratorLike() && !member.getUserId().equals(actorUserId)) {
                notificationService.createNotificationUnlessUnread(
                        member.getUserId(),
                        "Pages ready for review",
                        describe(pageCount) + " in " + project.getName() + " waiting for review.",
                        Notification.NotificationType.PAGE_REVIEW_REQUESTED,
                        project.getId(),
                        "Project",
                        "/tasks?view=review&project=" + project.getId()
                );
            }
        }
    }

    private void notifyChangesRequested(Project project, int pageCount, String actorUserId, String comment,
                                        Collection<String> recipients) {
        String message = describe(pageCount) + " in " + project.getName() + " sent back for changes."
                + (comment == null ? "" : " Comment: " + comment);
        Set<String> uniqueRecipients = new LinkedHashSet<>(recipients);
        uniqueRecipients.remove(actorUserId);
        for (String userId : uniqueRecipients) {
            notificationService.createNotification(
                    userId,
                    "Changes requested",
                    message,
                    Notification.NotificationType.PAGE_CHANGES_REQUESTED,
                    project.getId(),
                    "Project",
                    "/project/" + project.getId()
            );
        }
    }

    private String describe(int pageCount) {
        return pageCount == 1 ? "1 page" : pageCount + " pages";
    }

    private void pushAfterCommit(List<String> userIds, String workspaceId, String projectId, List<String> pageIds) {
        Runnable publish = () -> taskExecutor.execute(() -> {
            for (String userId : userIds) {
                notificationBridgeClient.pushPageWorkflowChanged(userId, workspaceId, projectId, pageIds, "page-workflow-service");
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
