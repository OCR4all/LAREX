package de.uniwue.zpd.dachs.larex.backend.service.task;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskActivityLogRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskCommentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskProjectLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskReminderRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceDeleteTest {

    @Mock TaskRepository taskRepository;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock WorkspaceQueryService workspaceQueryService;
    @Mock NotificationService notificationService;
    @Mock UserService userService;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock SubtaskRepository subtaskRepository;
    @Mock TaskActivityLogRepository taskActivityLogRepository;
    @Mock TaskCommentRepository taskCommentRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock TaskProjectLinkRepository taskProjectLinkRepository;
    @Mock TaskReminderRepository taskReminderRepository;
    @Mock PageWorkflowService pageWorkflowService;
    @Mock TaskActivityService taskActivityService;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                taskRepository,
                workspaceAccessService,
                workspaceMemberRepository,
                workspaceQueryService,
                notificationService,
                userService,
                authorizationPolicyService,
                subtaskRepository,
                taskActivityLogRepository,
                taskCommentRepository,
                taskPageLinkRepository,
                taskProjectLinkRepository,
                taskReminderRepository,
                pageWorkflowService,
                taskActivityService
        );
    }

    @Test
    void deleteTaskRemovesSubtasksAndAllTaskOwnedRecords() {
        Task task = new Task("Task", null, "creator-1", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        task.setSyncLinkedPageStates(true);
        TaskPageLink pageLink = new TaskPageLink(
                "task-1",
                "page-1",
                TaskPageLink.LinkType.MANUAL,
                "creator-1"
        );

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(taskPageLinkRepository.findByTaskId("task-1")).thenReturn(List.of(pageLink));

        service.deleteTask("task-1", "editor-1");

        verify(workspaceAccessService).requireManageTasksAccess("workspace-1", "editor-1");
        verify(subtaskRepository).deleteByTaskId("task-1");
        verify(taskReminderRepository).deleteByTaskId("task-1");
        verify(taskCommentRepository).deleteByTaskId("task-1");
        verify(taskActivityLogRepository).deleteByTaskId("task-1");
        verify(taskProjectLinkRepository).deleteByTaskId("task-1");
        verify(taskPageLinkRepository).deleteByTaskId("task-1");
        verify(pageWorkflowService).recomputeForExistingPageIds(List.of("page-1"));

        InOrder deletionOrder = inOrder(subtaskRepository, taskRepository);
        deletionOrder.verify(subtaskRepository).deleteByTaskId("task-1");
        deletionOrder.verify(taskRepository).delete(task);
        deletionOrder.verify(taskRepository).flush();
    }
}
