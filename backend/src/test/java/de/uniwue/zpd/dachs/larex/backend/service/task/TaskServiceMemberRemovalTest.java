package de.uniwue.zpd.dachs.larex.backend.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class TaskServiceMemberRemovalTest {

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
    void removedMemberIsUnassignedAndTheirOpenTasksGoToTheRemainingAssignees() {
        Task task = task("creator-1", "editor-1", "editor-2");
        when(taskRepository.findAssignedInWorkspace("workspace-1", "editor-1", null, true)).thenReturn(List.of(task));

        service.removeMemberFromAssignments("workspace-1", "editor-1", "owner-1");

        assertEquals(List.of("editor-2"), task.getAssignedUserIds());
        verify(subtaskService).redistributeOpenTasks(task, Set.of("editor-1"), List.of("editor-2"));
        verify(taskActivityService).logAssigneesChanged("task-1", "owner-1", List.of(), List.of("editor-1"));
        verify(taskReminderRepository).deleteByUserIdAndTaskIdIn("editor-1", List.of("task-1"));
    }

    @Test
    void creatorTakesOverWhenTheOnlyAssigneeIsRemoved() {
        Task task = task("creator-1", "editor-1");
        when(taskRepository.findAssignedInWorkspace("workspace-1", "editor-1", null, true)).thenReturn(List.of(task));
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "creator-1")).thenReturn(true);

        service.removeMemberFromAssignments("workspace-1", "editor-1", "editor-1");

        assertEquals(List.of("creator-1"), task.getAssignedUserIds());
        verify(subtaskService).redistributeOpenTasks(task, Set.of("editor-1"), List.of("creator-1"));
        verify(taskActivityService).logAssigneesChanged("task-1", "editor-1", List.of("creator-1"), List.of("editor-1"));
    }

    @Test
    void creatorWhoLeftDoesNotTakeOver() {
        Task task = task("creator-1", "editor-1");
        when(taskRepository.findAssignedInWorkspace("workspace-1", "editor-1", null, true)).thenReturn(List.of(task));
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "creator-1")).thenReturn(false);

        service.removeMemberFromAssignments("workspace-1", "editor-1", "owner-1");

        assertEquals(List.of(), task.getAssignedUserIds());
        verify(subtaskService).redistributeOpenTasks(task, Set.of("editor-1"), List.of());
    }

    @Test
    void memberWithoutAssignmentsChangesNothing() {
        when(taskRepository.findAssignedInWorkspace("workspace-1", "editor-1", null, true)).thenReturn(List.of());

        service.removeMemberFromAssignments("workspace-1", "editor-1", "owner-1");

        verify(taskReminderRepository, never()).deleteByUserIdAndTaskIdIn(anyString(), org.mockito.ArgumentMatchers.any());
    }

    private Task task(String creatorId, String... assignees) {
        Task task = new Task("Task", null, creatorId, Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        task.setAssignedUserIds(new ArrayList<>(List.of(assignees)));
        return task;
    }
}
