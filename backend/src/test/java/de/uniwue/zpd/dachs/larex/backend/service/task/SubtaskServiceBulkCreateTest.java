package de.uniwue.zpd.dachs.larex.backend.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.dto.SubtaskDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubtaskServiceBulkCreateTest {

    @Mock SubtaskRepository subtaskRepository;
    @Mock TaskRepository taskRepository;
    @Mock PageRepository pageRepository;
    @Mock TaskActivityService activityService;
    @Mock UserService userService;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock WorkspaceQueryService workspaceQueryService;
    @Mock TaskQueueRealtimePublisher taskQueueRealtimePublisher;

    private SubtaskService service;

    @BeforeEach
    void setUp() {
        service = new SubtaskService(
                subtaskRepository,
                taskRepository,
                pageRepository,
                activityService,
                userService,
                authorizationPolicyService,
                workspaceAccessService,
                workspaceQueryService,
                taskQueueRealtimePublisher
        );
    }

    @Test
    void createsBatchWithConsecutiveSortOrdersAndOneSave() {
        Task task = task();
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(authorizationPolicyService.canManageTasks("workspace-1", "manager")).thenReturn(true);
        when(subtaskRepository.getNextSortOrder("task-1")).thenReturn(4);
        when(subtaskRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Subtask> subtasks = invocation.getArgument(0);
            int index = 0;
            for (Subtask subtask : subtasks) {
                subtask.setId("subtask-" + index++);
            }
            return (List<Subtask>) subtasks;
        });
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of());
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of());

        List<SubtaskDto.Response> created = service.createSubtasksWithPages(
                "task-1",
                "manager",
                List.of(
                        new SubtaskDto.CreateWithPageRequest("First", "page-1", null, "Description"),
                        new SubtaskDto.CreateWithPageRequest("Second", "page-2", null, null)
                )
        );

        assertEquals(List.of(4, 5), created.stream().map(SubtaskDto.Response::sortOrder).toList());
        verify(subtaskRepository).saveAll(any());
        verify(taskQueueRealtimePublisher).publishAfterCommit(anyList(), eq("workspace-1"));
    }

    @Test
    void rejectsBatchWithoutTaskManagementAccess() {
        Task task = task();
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(authorizationPolicyService.canManageTasks("workspace-1", "editor")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.createSubtasksWithPages(
                "task-1",
                "editor",
                List.of(new SubtaskDto.CreateWithPageRequest("First", "page-1", null, null))
        ));
    }

    private Task task() {
        Task task = new Task("Task", null, "creator", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        return task;
    }
}
