package de.uniwue.zpd.dachs.larex.backend.service.task;

import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskProjectLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskLinkCleanupServiceTest {

    @Mock private PageRepository pageRepository;
    @Mock private TaskPageLinkRepository taskPageLinkRepository;
    @Mock private TaskProjectLinkRepository taskProjectLinkRepository;
    @Mock private SubtaskRepository subtaskRepository;
    @Mock private TaskProgressService taskProgressService;

    private TaskLinkCleanupService service;

    @BeforeEach
    void setUp() {
        service = new TaskLinkCleanupService(pageRepository, taskPageLinkRepository, taskProjectLinkRepository,
                subtaskRepository, taskProgressService);
    }

    @Test
    void removePagesDeletesLinksAndSubtasksThenRecomputesTheirTasks() {
        when(subtaskRepository.findTaskIdsByPageIdIn(List.of("page-1"))).thenReturn(List.of("task-1"));

        service.removePages(List.of("page-1"), "user-1");

        InOrder order = inOrder(subtaskRepository, taskPageLinkRepository, taskProgressService);
        order.verify(subtaskRepository).findTaskIdsByPageIdIn(List.of("page-1"));
        order.verify(taskPageLinkRepository).deleteByPageIdIn(List.of("page-1"));
        order.verify(subtaskRepository).deleteByPageIdIn(List.of("page-1"));
        order.verify(taskProgressService).recompute(List.of("task-1"), "user-1");
    }

    @Test
    void removeProjectCleansItsPagesAndProjectLinks() {
        when(pageRepository.findIdsByProjectId("project-1")).thenReturn(List.of("page-1", "page-2"));
        when(subtaskRepository.findTaskIdsByPageIdIn(List.of("page-1", "page-2"))).thenReturn(List.of());

        service.removeProject("project-1", "user-1");

        verify(subtaskRepository).deleteByPageIdIn(List.of("page-1", "page-2"));
        verify(taskProjectLinkRepository).deleteByProjectId("project-1");
    }

    @Test
    void removePagesIgnoresEmptyInput() {
        service.removePages(List.of(), "user-1");

        verify(taskPageLinkRepository, never()).deleteByPageIdIn(anyList());
        verify(taskProgressService, never()).recompute(any(java.util.Collection.class), any());
    }
}
