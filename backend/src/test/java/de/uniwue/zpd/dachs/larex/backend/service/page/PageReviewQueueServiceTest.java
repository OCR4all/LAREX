package de.uniwue.zpd.dachs.larex.backend.service.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.dto.ReviewQueueDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.entity.TaskPageLink;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageReviewQueueServiceTest {

    private static final String WORKSPACE = "workspace-1";
    private static final String CURATOR = "curator-1";

    @Mock PageRepository pageRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock TaskRepository taskRepository;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock WorkspaceQueryService workspaceQueryService;
    @Mock AnnotationLeaseService annotationLeaseService;
    @Mock UserService userService;

    private PageReviewQueueService service;
    private Project alpha;
    private Project beta;

    @BeforeEach
    void setUp() {
        service = new PageReviewQueueService(pageRepository, taskPageLinkRepository, taskRepository,
                workspaceAccessService, workspaceQueryService, annotationLeaseService, userService);
        Library library = new Library(WORKSPACE, "Library");
        alpha = project("project-a", "Alpha", library);
        beta = project("project-b", "Beta", library);
        lenient().when(workspaceQueryService.findWorkspaceById(WORKSPACE))
                .thenReturn(Optional.of(new TeamWorkspace("Workspace", null, "owner")));
        lenient().when(annotationLeaseService.findActiveEditors(any(), eq(CURATOR))).thenReturn(Map.of());
        lenient().when(userService.getUsersByIds(anyList())).thenReturn(Map.of());
        lenient().when(taskPageLinkRepository.findByPageIdIn(any())).thenReturn(List.of());
        lenient().when(taskRepository.findAllById(any())).thenReturn(List.of());
    }

    @Test
    void requiresReviewAccess() {
        doThrow(new SecurityException("denied")).when(workspaceAccessService).requireReviewPagesAccess(WORKSPACE, "editor-1");

        assertThrows(SecurityException.class, () -> service.getQueue(WORKSPACE, null, null, null, "editor-1"));
    }

    @Test
    void ordersByDueDateThenProjectAndPageOrder() {
        Page betaPage = page("beta-1", beta, 10, "editor-1");
        Page alphaSecond = page("alpha-2", alpha, 20, "editor-1");
        Page alphaFirst = page("alpha-1", alpha, 10, "editor-1");
        Page urgent = page("beta-urgent", beta, 30, "editor-1");
        when(pageRepository.findInReviewByWorkspaceId(WORKSPACE)).thenReturn(List.of(betaPage, alphaSecond, alphaFirst, urgent));
        Task task = task("task-1", LocalDateTime.of(2026, 11, 10, 0, 0));
        when(taskPageLinkRepository.findByPageIdIn(any())).thenReturn(List.of(link("task-1", "beta-urgent")));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        ReviewQueueDto.Response queue = service.getQueue(WORKSPACE, null, null, null, CURATOR);

        assertThat(queue.pages()).extracting(ReviewQueueDto.PageResponse::pageId)
                .containsExactly("beta-urgent", "alpha-1", "alpha-2", "beta-1");
        assertThat(queue.pages().getFirst().assignments()).extracting(ReviewQueueDto.AssignmentSummary::title).containsExactly("Task");
        assertThat(queue.totalPageCount()).isEqualTo(4);
    }

    @Test
    void filtersByProjectSubmitterAndAssignment() {
        Page mine = page("mine", alpha, 10, "editor-1");
        Page other = page("other", alpha, 20, "editor-2");
        Page elsewhere = page("elsewhere", beta, 10, "editor-1");
        when(pageRepository.findInReviewByWorkspaceId(WORKSPACE)).thenReturn(List.of(mine, other, elsewhere));

        assertThat(service.getQueue(WORKSPACE, "project-a", null, null, CURATOR).pages())
                .extracting(ReviewQueueDto.PageResponse::pageId).containsExactly("mine", "other");
        assertThat(service.getQueue(WORKSPACE, null, null, "editor-2", CURATOR).pages())
                .extracting(ReviewQueueDto.PageResponse::pageId).containsExactly("other");

        when(taskPageLinkRepository.findByPageIdIn(any())).thenReturn(List.of(link("task-1", "elsewhere")));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task("task-1", null)));
        assertThat(service.getQueue(WORKSPACE, null, "task-1", null, CURATOR).pages())
                .extracting(ReviewQueueDto.PageResponse::pageId).containsExactly("elsewhere");
    }

    @Test
    void blocksPagesHeldByAnotherReviewerOrAnAction() {
        Page held = page("held", alpha, 10, "editor-1");
        Page running = page("running", alpha, 20, "editor-1");
        running.setLocked(true);
        running.setLockedReason("LAREX Action running: OCR");
        Page free = page("free", alpha, 30, "editor-1");
        when(pageRepository.findInReviewByWorkspaceId(WORKSPACE)).thenReturn(List.of(held, running, free));
        when(annotationLeaseService.findActiveEditors(any(), eq(CURATOR))).thenReturn(Map.of("held", "Dana"));

        ReviewQueueDto.Response queue = service.getQueue(WORKSPACE, null, null, null, CURATOR);

        assertThat(queue.blockedPageCount()).isEqualTo(2);
        assertThat(queue.pages().get(0).blockedReason()).isEqualTo("Dana is reviewing this page");
        assertThat(queue.pages().get(1).blockedReason()).isEqualTo("LAREX Action running: OCR");
        assertThat(queue.pages().get(2).blocked()).isFalse();
    }

    @Test
    void capsTheWindowButReportsTheTotal() {
        List<Page> pages = new ArrayList<>();
        for (int index = 0; index < PageReviewQueueService.MAX_QUEUE_PAGES + 5; index++) {
            pages.add(page("page-" + index, alpha, index, "editor-1"));
        }
        when(pageRepository.findInReviewByWorkspaceId(WORKSPACE)).thenReturn(pages);

        ReviewQueueDto.Response queue = service.getQueue(WORKSPACE, null, null, null, CURATOR);

        assertThat(queue.pages()).hasSize(PageReviewQueueService.MAX_QUEUE_PAGES);
        assertThat(queue.totalPageCount()).isEqualTo(PageReviewQueueService.MAX_QUEUE_PAGES + 5L);
    }

    private Project project(String id, String name, Library library) {
        Project project = new Project(name, null, library);
        project.setId(id);
        return project;
    }

    private Page page(String id, Project project, int sortOrder, String submitter) {
        Page page = new Page(id, null, project);
        page.setId(id);
        page.setSortOrder(sortOrder);
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(submitter);
        return page;
    }

    private Task task(String id, LocalDateTime dueDate) {
        Task task = new Task("Task", null, CURATOR, Task.TaskPriority.MEDIUM, WORKSPACE);
        task.setId(id);
        task.setDueDate(dueDate);
        return task;
    }

    private TaskPageLink link(String taskId, String pageId) {
        TaskPageLink link = new TaskPageLink();
        link.setTaskId(taskId);
        link.setPageId(pageId);
        return link;
    }
}
