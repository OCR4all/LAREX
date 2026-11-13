package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.dto.TaskCommentDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskComment;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskCommentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCommentServiceTest {

    @Mock TaskCommentRepository commentRepository;
    @Mock TaskRepository taskRepository;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock UserService userService;
    @Mock TaskActivityService activityService;
    @Mock NotificationService notificationService;
    @Mock AuthorizationPolicyService authorizationPolicyService;

    private TaskCommentService service;
    private Task task;

    @BeforeEach
    void setUp() {
        service = new TaskCommentService(commentRepository, taskRepository, workspaceAccessService,
                userService, activityService, notificationService, authorizationPolicyService);
        task = new Task("Task", null, "curator", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        task.setAssignedUserIds(new ArrayList<>(List.of("editor")));
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
    }

    @Test
    void assigneesCanComment() {
        when(authorizationPolicyService.canCommentOnTask(task, "editor")).thenReturn(true);
        when(commentRepository.save(any(TaskComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of());

        TaskCommentDto.Response response = service.createComment("task-1", "editor", new TaskCommentDto.CreateRequest("Question"));

        assertEquals("Question", response.content());
        verify(notificationService).createTaskCommentAddedNotification("curator", "Task", "task-1");
    }

    @Test
    void membersOutsideTheAssignmentCanNotComment() {
        when(authorizationPolicyService.canCommentOnTask(task, "other-editor")).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> service.createComment("task-1", "other-editor", new TaskCommentDto.CreateRequest("Hi")));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void mentionsOnlyNotifyWorkspaceMembers() {
        when(authorizationPolicyService.canCommentOnTask(task, "editor")).thenReturn(true);
        when(userService.getUserIdsByUsernames(List.of("member", "stranger"))).thenReturn(List.of("member-id", "stranger-id"));
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "member-id")).thenReturn(true);
        when(workspaceAccessService.hasWorkspaceAccess("workspace-1", "stranger-id")).thenReturn(false);
        when(commentRepository.save(any(TaskComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of());

        TaskCommentDto.Response response = service.createComment("task-1", "editor",
                new TaskCommentDto.CreateRequest("@member @stranger look"));

        assertEquals(List.of("member-id"), response.mentionedUserIds());
        verify(notificationService).createTaskMentionedNotification("member-id", "Task", "task-1");
        verify(notificationService, never()).createTaskMentionedNotification(org.mockito.ArgumentMatchers.eq("stranger-id"), anyString(), anyString());
    }
}
