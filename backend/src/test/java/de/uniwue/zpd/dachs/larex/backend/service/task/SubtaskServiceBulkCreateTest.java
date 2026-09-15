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
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubtaskServiceBulkCreateTest {

    @Mock SubtaskRepository subtaskRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock TaskRepository taskRepository;
    @Mock PageRepository pageRepository;
    @Mock Page page1;
    @Mock Page page2;
    @Mock Project project;
    @Mock Library library;
    @Mock TaskActivityService activityService;
    @Mock UserService userService;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock WorkspaceQueryService workspaceQueryService;
    @Mock TaskQueueRealtimePublisher taskQueueRealtimePublisher;
    @Mock PageWorkflowService pageWorkflowService;

    private SubtaskService service;

    @BeforeEach
    void setUp() {
        service = new SubtaskService(
            subtaskRepository,
                taskPageLinkRepository,
                taskRepository,
                pageRepository,
                activityService,
                userService,
                authorizationPolicyService,
                workspaceAccessService,
                workspaceQueryService,
                taskQueueRealtimePublisher,
                pageWorkflowService
        );
    }

    @Test
    void createsBatchWithConsecutiveSortOrdersAndOneSave() {
        Task task = task();
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(authorizationPolicyService.canManageTasks("workspace-1", "manager")).thenReturn(true);
        when(subtaskRepository.getNextSortOrder("task-1")).thenReturn(4);
        task.setAssignedUserIds(List.of("member-a", "member-b"));
        when(page1.getId()).thenReturn("page-1");
        when(page1.getProject()).thenReturn(project);
        when(page2.getId()).thenReturn("page-2");
        when(page2.getProject()).thenReturn(project);
        when(project.getLibrary()).thenReturn(library);
        when(library.getWorkspaceId()).thenReturn("workspace-1");
        when(pageRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(page1, page2));
        when(subtaskRepository.findByTaskIdAndPageIdIn(eq("task-1"), anyList())).thenReturn(List.of());
        when(subtaskRepository.findByTaskIdOrderBySortOrderAsc("task-1")).thenReturn(List.of());
        when(taskPageLinkRepository.existsByTaskIdAndPageId(any(), any())).thenReturn(false);
        when(subtaskRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<Subtask> subtasks = invocation.getArgument(0);
            int index = 0;
            for (Subtask subtask : subtasks) {
                subtask.setId("subtask-" + index++);
            }
            return (List<Subtask>) subtasks;
        });
        when(userService.getUsersByIds(anyList())).thenReturn(Map.of());
        List<SubtaskDto.Response> created = service.createSubtasksFromPages(
                "task-1",
                "manager",
                new SubtaskDto.CreateFromPagesRequest(List.of("page-1", "page-2"))
        );

        assertEquals(List.of(4, 5), created.stream().map(SubtaskDto.Response::sortOrder).toList());
        assertEquals(Set.of("member-a", "member-b"), created.stream().map(SubtaskDto.Response::assignedUserId).collect(java.util.stream.Collectors.toSet()));
        verify(subtaskRepository).saveAll(any());
        verify(taskQueueRealtimePublisher).publishAfterCommit(anyList(), eq("workspace-1"));
    }

    @Test
    void rejectsBatchWithoutTaskManagementAccess() {
        Task task = task();
        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(authorizationPolicyService.canManageTasks("workspace-1", "editor")).thenReturn(false);

        assertThrows(RuntimeException.class, () -> service.createSubtasksFromPages(
                "task-1",
                "editor",
                new SubtaskDto.CreateFromPagesRequest(List.of("page-1"))
        ));
    }

    @Test
    void completingPageTaskRecomputesItsWorkflowState() {
        Task task = task();
        task.setSyncLinkedPageStates(true);
        Subtask subtask = new Subtask("task-1", "Task", 0);
        subtask.setId("subtask-1");
        subtask.setPageId("page-1");
        subtask.setAssignedUserId("manager");

        when(taskRepository.findById("task-1")).thenReturn(Optional.of(task));
        when(authorizationPolicyService.canManageTasks("workspace-1", "manager")).thenReturn(true);
        when(subtaskRepository.findByTaskIdAndIdIn("task-1", List.of("subtask-1"))).thenReturn(List.of(subtask));
        when(subtaskRepository.markCompletedByTaskIdAndIdIn(
                eq("task-1"), eq(List.of("subtask-1")), any(), eq("manager"))).thenReturn(1);

        service.bulkComplete("task-1", "manager", new SubtaskDto.BulkRequest(List.of("subtask-1")));

        verify(pageWorkflowService).recomputeForExistingPageIds(List.of("page-1"), "manager");
    }

    private Task task() {
        Task task = new Task("Task", null, "creator", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        return task;
    }
}
