package de.uniwue.zpd.dachs.larex.backend.service.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PageWorkflowServiceTest {

    @Mock PageRepository pageRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock AnnotationLeaseService annotationLeaseService;

    private PageWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new PageWorkflowService(pageRepository, projectRepository, taskPageLinkRepository,
                authorizationPolicyService, annotationLeaseService);
    }

    @Test
    void manualDoneUsesEditorPermissionAndCreatesEffectiveLockWithoutTransientLock() {
        Project project = project();
        Page page = page("page-1", project);
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "editor-1")).thenReturn(true);
        when(pageRepository.findByIdAndProjectIdForUpdate("page-1", "project-1")).thenReturn(Optional.of(page));
        when(pageRepository.save(page)).thenReturn(page);

        Page updated = service.updateState("project-1", "page-1", Page.WorkflowState.DONE, "editor-1");

        assertEquals(Page.WorkflowState.DONE, updated.getWorkflowState());
        assertFalse(updated.isLocked());
        assertTrue(updated.isEffectivelyLocked());
        verify(annotationLeaseService).assertNoOtherActiveEditor("page-1", "editor-1");

        updated.setLocked(true);
        updated.setLocked(false);
        assertTrue(updated.isEffectivelyLocked(), "releasing a transient lock must not reopen Done pages");
    }

    @Test
    void recomputeRequiresAllNonCancelledTasksToComplete() {
        Project project = project();
        Page page = page("page-1", project);
        Task completed = task(Task.TaskStatus.COMPLETED);
        Task inProgress = task(Task.TaskStatus.IN_PROGRESS);

        when(pageRepository.findAllByIdInForUpdate(List.of("page-1"))).thenReturn(List.of(page));
        when(taskPageLinkRepository.findSyncEnabledTasksByPageIds(List.of("page-1")))
                .thenReturn(List.of(new Object[]{"page-1", completed}, new Object[]{"page-1", inProgress}));
        when(pageRepository.saveAll(List.of(page))).thenReturn(List.of(page));

        service.recomputeForPageIds(List.of("page-1"));
        assertEquals(Page.WorkflowState.IN_PROGRESS, page.getWorkflowState());

        inProgress.setStatus(Task.TaskStatus.COMPLETED);
        service.recomputeForPageIds(List.of("page-1"));
        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
        verify(annotationLeaseService).assertNoOtherActiveEditor("page-1", null);
    }

    @Test
    void deletionRecomputeIgnoresPagesThatNoLongerExist() {
        Project project = project();
        Page existingPage = page("page-1", project);
        when(pageRepository.findAllByIdInForUpdate(List.of("page-1", "deleted-page")))
                .thenReturn(List.of(existingPage));
        when(taskPageLinkRepository.findSyncEnabledTasksByPageIds(List.of("page-1")))
                .thenReturn(List.of());
        when(pageRepository.saveAll(List.of(existingPage))).thenReturn(List.of(existingPage));

        List<Page> updated = service.recomputeForExistingPageIds(List.of("page-1", "deleted-page"));

        assertEquals(List.of(existingPage), updated);
        assertEquals(Page.WorkflowState.OPEN, existingPage.getWorkflowState());
    }

    @Test
    void transientActionLockRejectsManualWorkflowChanges() {
        Project project = project();
        Page page = page("page-1", project);
        page.setLocked(true);
        page.setLockedReason("LAREX Action running: OCR");
        when(projectRepository.findById("project-1")).thenReturn(Optional.of(project));
        when(authorizationPolicyService.canAccessWorkspace("workspace-1", "editor-1")).thenReturn(true);
        when(pageRepository.findByIdAndProjectIdForUpdate("page-1", "project-1")).thenReturn(Optional.of(page));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateState("project-1", "page-1", Page.WorkflowState.DONE, "editor-1"));
        assertEquals(423, error.getStatusCode().value());
    }

    private Project project() {
        Project project = new Project();
        project.setId("project-1");
        project.setLibrary(new Library("workspace-1", "Workspace"));
        return project;
    }

    private Page page(String id, Project project) {
        Page page = new Page("Page", null, project);
        page.setId(id);
        return page;
    }

    private Task task(Task.TaskStatus status) {
        Task task = new Task("Task", null, "curator-1", Task.TaskPriority.MEDIUM, "workspace-1");
        task.setStatus(status);
        return task;
    }
}
