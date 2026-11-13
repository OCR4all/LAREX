package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskProgressServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock SubtaskRepository subtaskRepository;
    @Mock TaskActivityService activityService;
    @Mock NotificationService notificationService;
    @Mock TaskQueueRealtimePublisher taskQueueRealtimePublisher;

    private TaskProgressService service;

    @BeforeEach
    void setUp() {
        service = new TaskProgressService(taskRepository, subtaskRepository, activityService,
                notificationService, taskQueueRealtimePublisher);
    }

    @Test
    void derivesStatusFromCompletedTasks() {
        assertEquals(Task.TaskStatus.OPEN, TaskProgressService.deriveStatus(0, 0));
        assertEquals(Task.TaskStatus.OPEN, TaskProgressService.deriveStatus(3, 0));
        assertEquals(Task.TaskStatus.IN_PROGRESS, TaskProgressService.deriveStatus(3, 1));
        assertEquals(Task.TaskStatus.COMPLETED, TaskProgressService.deriveStatus(3, 3));
    }

    @Test
    void completingTheLastTaskCompletesTheAssignmentAndNotifiesOthersOnce() {
        Task task = task(Task.TaskStatus.IN_PROGRESS);
        progress(2, 2, task);

        service.recompute("task-1", "editor-1");

        assertEquals(Task.TaskStatus.COMPLETED, task.getStatus());
        assertNotNull(task.getCompletedAt());
        assertEquals("editor-1", task.getCompletedByUserId());
        verify(activityService).logStatusDerived("task-1", "editor-1", Task.TaskStatus.IN_PROGRESS, Task.TaskStatus.COMPLETED);
        verify(notificationService).createTaskCompletedNotification("creator-1", "Task", "task-1");
        verify(notificationService, never()).createTaskCompletedNotification("editor-1", "Task", "task-1");
    }

    @Test
    void reopeningATaskMovesACompletedAssignmentBackAndClearsCompletion() {
        Task task = task(Task.TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedByUserId("editor-1");
        progress(2, 1, task);

        service.recompute("task-1", "curator-1");

        assertEquals(Task.TaskStatus.IN_PROGRESS, task.getStatus());
        assertNull(task.getCompletedAt());
        assertNull(task.getCompletedByUserId());
        verify(notificationService, never()).createTaskCompletedNotification(anyString(), anyString(), anyString());
    }

    @Test
    void pausedAssignmentsKeepTheirStatus() {
        Task task = task(Task.TaskStatus.PAUSED);
        progress(2, 2, task);

        service.recompute("task-1", "editor-1");

        assertEquals(Task.TaskStatus.PAUSED, task.getStatus());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void unchangedStatusWritesNothing() {
        Task task = task(Task.TaskStatus.IN_PROGRESS);
        progress(3, 1, task);

        service.recompute("task-1", "editor-1");

        verify(taskRepository, never()).save(any());
        verify(activityService, never()).logStatusDerived(any(), any(), any(), any());
    }

    @Test
    void assignmentWithoutTasksIsOpen() {
        Task task = task(Task.TaskStatus.IN_PROGRESS);
        when(subtaskRepository.countProgressByTaskIds(any())).thenReturn(List.of());
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        service.recompute("task-1", "curator-1");

        assertEquals(Task.TaskStatus.OPEN, task.getStatus());
    }

    private void progress(long total, long completed, Task task) {
        when(subtaskRepository.countProgressByTaskIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{"task-1", total, completed}));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));
    }

    private Task task(Task.TaskStatus status) {
        Task task = new Task("Task", null, "creator-1", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setId("task-1");
        task.setStatus(status);
        task.setAssignedUserIds(new java.util.ArrayList<>(List.of("editor-1")));
        return task;
    }
}
