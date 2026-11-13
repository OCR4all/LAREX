package de.uniwue.zpd.dachs.larex.backend.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServicePauseTest {

    @Mock TaskRepository taskRepository;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock WorkspaceQueryService workspaceQueryService;
    @Mock NotificationService notificationService;
    @Mock UserService userService;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock SubtaskRepository subtaskRepository;
    @Mock SubtaskService subtaskService;
    @Mock TaskActivityLogRepository taskActivityLogRepository;
    @Mock TaskCommentRepository taskCommentRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock TaskProjectLinkRepository taskProjectLinkRepository;
    @Mock TaskReminderRepository taskReminderRepository;
    @Mock PageWorkflowService pageWorkflowService;
    @Mock PageRepository pageRepository;
    @Mock TaskQueueRealtimePublisher taskQueueRealtimePublisher;
    @Mock TaskProgressService taskProgressService;
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
                subtaskService,
                taskActivityLogRepository,
                taskCommentRepository,
                taskPageLinkRepository,
                taskProjectLinkRepository,
                taskReminderRepository,
                pageWorkflowService,
                pageRepository,
                taskQueueRealtimePublisher,
                taskProgressService,
                taskActivityService
        );
    }

    @Test
    void pausingAndResumingACompletedAssignmentKeepsItsCompletionDate() {
        java.time.LocalDateTime completedAt = java.time.LocalDateTime.of(2026, 10, 1, 9, 0);
        Task task = task(Task.TaskStatus.COMPLETED);
        task.setCompletedAt(completedAt);
        task.setCompletedByUserId("editor-1");
        when(taskRepository.findById("task-1")).thenReturn(java.util.Optional.of(task));
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "curator-1")).thenReturn(true);
        when(workspaceQueryService.findWorkspaceById("workspace-1")).thenReturn(java.util.Optional.of(
                org.mockito.Mockito.mock(de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace.class)));
        when(taskProgressService.deriveStatus("task-1")).thenReturn(Task.TaskStatus.COMPLETED);

        service.setPaused("task-1", "curator-1", true);
        assertEquals(Task.TaskStatus.PAUSED, task.getStatus());
        assertEquals(completedAt, task.getCompletedAt());

        service.setPaused("task-1", "curator-1", false);
        assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
        assertEquals(completedAt, task.getCompletedAt());
        assertEquals("editor-1", task.getCompletedByUserId());
    }

    @Test
    void resumingIntoUnfinishedWorkClearsTheCompletionDate() {
        Task task = task(Task.TaskStatus.PAUSED);
        task.setCompletedAt(java.time.LocalDateTime.of(2026, 10, 1, 9, 0));
        when(taskRepository.findById("task-1")).thenReturn(java.util.Optional.of(task));
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "curator-1")).thenReturn(true);
        when(workspaceQueryService.findWorkspaceById("workspace-1")).thenReturn(java.util.Optional.of(
                org.mockito.Mockito.mock(de.uniwue.zpd.dachs.larex.backend.entity.workspace.AbstractWorkspace.class)));
        when(taskProgressService.deriveStatus("task-1")).thenReturn(Task.TaskStatus.IN_PROGRESS);

        service.setPaused("task-1", "curator-1", false);

        assertEquals(Task.TaskStatus.IN_PROGRESS, task.getStatus());
        assertNull(task.getCompletedAt());
    }

    private Task task(Task.TaskStatus status) {
        Task task = new Task("Task", null, "creator-1", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        task.setStatus(status);
        task.setAssignedUserIds(new ArrayList<>(List.of("editor-1")));
        return task;
    }
}
