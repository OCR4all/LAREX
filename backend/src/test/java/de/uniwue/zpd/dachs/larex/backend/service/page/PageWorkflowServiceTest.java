package de.uniwue.zpd.dachs.larex.backend.service.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent.Action;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.Subtask;
import de.uniwue.zpd.dachs.larex.backend.entity.Task;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationLeaseLockedException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageWorkflowEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.SubtaskRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration.AnnotationLeaseService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.PageWorkflowPublisher;
import de.uniwue.zpd.dachs.larex.backend.service.notification.TaskQueueRealtimePublisher;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.task.TaskActivityService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PageWorkflowServiceTest {

    private static final String PROJECT = "project-1";
    private static final String WORKSPACE = "workspace-1";
    private static final String EDITOR = "editor-1";
    private static final String CURATOR = "curator-1";

    @Mock PageRepository pageRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock SubtaskRepository subtaskRepository;
    @Mock TaskRepository taskRepository;
    @Mock PageWorkflowEventRepository eventRepository;
    @Mock AuthorizationPolicyService authorizationPolicyService;
    @Mock AnnotationLeaseService annotationLeaseService;
    @Mock TaskActivityService taskActivityService;
    @Mock TaskQueueRealtimePublisher taskQueueRealtimePublisher;
    @Mock PageWorkflowPublisher pageWorkflowPublisher;
    @Mock UserService userService;

    private PageWorkflowService service;
    private Project project;
    private Page page;

    @BeforeEach
    void setUp() {
        service = new PageWorkflowService(pageRepository, projectRepository, taskPageLinkRepository,
                subtaskRepository, taskRepository, eventRepository, authorizationPolicyService,
                annotationLeaseService, taskActivityService, taskQueueRealtimePublisher,
                pageWorkflowPublisher, userService);
        project = new Project();
        project.setId(PROJECT);
        project.setLibrary(new Library(WORKSPACE, "Workspace"));
        page = new Page("Page", null, project);
        page.setId("page-1");

        lenient().when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        lenient().when(authorizationPolicyService.canAccessWorkspace(eq(WORKSPACE), any())).thenReturn(true);
        lenient().when(authorizationPolicyService.canReviewPages(WORKSPACE, CURATOR)).thenReturn(true);
        lenient().when(authorizationPolicyService.canReviewPages(WORKSPACE, EDITOR)).thenReturn(false);
        lenient().when(pageRepository.findByIdInAndProjectIdForUpdate(List.of("page-1"), PROJECT)).thenReturn(List.of(page));
        lenient().when(pageRepository.findAllByIdInForUpdate(List.of("page-1"))).thenReturn(List.of(page));
        lenient().when(pageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskPageLinkRepository.findPageStateTaskSubtasksByPageIds(List.of("page-1"))).thenReturn(List.of());
        lenient().when(subtaskRepository.findCompletedAffectingPageState(List.of("page-1"))).thenReturn(List.of());
        lenient().when(subtaskRepository.findCompletedOnActiveAssignments(List.of("page-1"))).thenReturn(List.of());
    }

    private void transition(Action action, String comment, String userId) {
        service.transition(PROJECT, List.of("page-1"), action, comment, userId);
    }

    private int statusOf(ResponseStatusException error) {
        return error.getStatusCode().value();
    }

    @Test
    void submitMovesAnOpenPageToReviewAndRecordsTheSubmitter() {
        page.setChangesRequested(true);

        transition(Action.SUBMIT, "  Please check the margins ", EDITOR);

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
        assertEquals(EDITOR, page.getReviewSubmittedByUserId());
        assertFalse(page.getReviewSubmittedAt() == null);
        assertFalse(page.isChangesRequested());

        ArgumentCaptor<List<PageWorkflowEvent>> events = ArgumentCaptor.forClass(List.class);
        verify(eventRepository).saveAll(events.capture());
        PageWorkflowEvent event = events.getValue().getFirst();
        assertEquals(Action.SUBMIT, event.getAction());
        assertEquals(Page.WorkflowState.OPEN, event.getFromState());
        assertEquals(Page.WorkflowState.IN_REVIEW, event.getToState());
        assertEquals(EDITOR, event.getActorUserId());
        assertEquals("Please check the margins", event.getComment());
    }

    @Test
    void submittedPageIsReadOnlyForEditorsButNotForCurators() {
        transition(Action.SUBMIT, null, EDITOR);

        assertTrue(page.isEffectivelyLocked());
        assertTrue(page.isLockedFor(false));
        assertEquals("Page is in review", page.getLockedReasonFor(false));
        assertFalse(page.isLockedFor(true));
        assertNull(page.getLockedReasonFor(true));
    }

    @Test
    void reviewerDoesNotBypassOtherLocks() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setLocked(true);
        page.setLockedReason("LAREX Action running: OCR");

        assertTrue(page.isLockedFor(true));
        assertEquals("LAREX Action running: OCR", page.getLockedReasonFor(true));
    }

    @Test
    void curatorApprovesAPageInReview() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);

        transition(Action.APPROVE, null, CURATOR);

        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
        assertNull(page.getReviewSubmittedByUserId());
        assertTrue(page.isEffectivelyLocked());
        assertTrue(page.isLockedFor(true), "Done stays read-only for curators");
        verify(annotationLeaseService).assertNoOtherActiveEditor("page-1", CURATOR);
    }

    @Test
    void curatorMayApproveTheirOwnSubmission() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(CURATOR);

        transition(Action.APPROVE, null, CURATOR);

        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
    }

    @Test
    void editorsCannotApproveOrRequestChanges() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);

        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.APPROVE, null, EDITOR))));
        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.REQUEST_CHANGES, null, EDITOR))));
        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
    }

    @Test
    void requestChangesReopensSubtasksRevivesCompletedAssignmentsAndNotifies() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);
        Subtask subtask = new Subtask();
        subtask.setTaskId("task-1");
        subtask.setCompleted(true);
        subtask.setAssignedUserId("assignee-1");
        Task task = task(Task.TaskStatus.COMPLETED, Task.PageCompletion.NONE);
        task.setId("task-1");
        when(subtaskRepository.findCompletedOnActiveAssignments(List.of("page-1"))).thenReturn(List.of(subtask));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));

        transition(Action.REQUEST_CHANGES, "Line 3 is wrong", CURATOR);

        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
        assertTrue(page.isChangesRequested());
        assertFalse(subtask.isCompleted());
        assertNull(subtask.getCompletedAt());
        assertEquals(Task.TaskStatus.IN_PROGRESS, task.getStatus());
        verify(taskActivityService).logStatusChanged("task-1", CURATOR, Task.TaskStatus.COMPLETED, Task.TaskStatus.IN_PROGRESS);
        verify(taskQueueRealtimePublisher).publishAfterCommit(Set.of("assignee-1"), WORKSPACE);
        verify(pageWorkflowPublisher).publish(eq(project), anyList(), eq(Action.REQUEST_CHANGES), eq(CURATOR),
                eq("Line 3 is wrong"), eq(new java.util.LinkedHashSet<>(List.of(EDITOR, "assignee-1"))));
    }

    @Test
    void withdrawOnlyReopensSubtasksThatAffectPageState() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);

        transition(Action.WITHDRAW, null, EDITOR);

        verify(subtaskRepository).findCompletedAffectingPageState(List.of("page-1"));
        verify(subtaskRepository, never()).findCompletedOnActiveAssignments(any());
    }

    @Test
    void finishedChangesReturnToReviewEvenWithoutARequiredReview() {
        page.setChangesRequested(true);
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.DONE), true);

        service.recomputeForPageIds(List.of("page-1"), EDITOR);

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
        assertFalse(page.isChangesRequested());
    }

    @Test
    void submitterCanWithdraw() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);

        transition(Action.WITHDRAW, null, EDITOR);

        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
        assertNull(page.getReviewSubmittedByUserId());
        assertFalse(page.isChangesRequested());
    }

    @Test
    void onlyTheSubmitterCanWithdraw() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);

        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.WITHDRAW, null, "editor-2"))));
        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.WITHDRAW, null, CURATOR))));
    }

    @Test
    void withdrawIsBlockedWhileSomeoneElseIsOnThePage() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);
        doThrow(new AnnotationLeaseLockedException("Curator is currently editing this page.", null, "lease-held-by-other-user"))
                .when(annotationLeaseService).assertNoOtherActiveEditor("page-1", EDITOR);

        assertThrows(AnnotationLeaseLockedException.class, () -> transition(Action.WITHDRAW, null, EDITOR));

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
    }

    @Test
    void editorsCanMarkDoneAndReopenWithoutARequiredReview() {
        transition(Action.MARK_DONE, null, EDITOR);
        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());

        transition(Action.REOPEN, null, EDITOR);
        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
    }

    @Test
    void projectPolicyLimitsMarkDoneAndReopenToCurators() {
        project.setReviewRequired(true);

        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.MARK_DONE, null, EDITOR))));
        transition(Action.MARK_DONE, null, CURATOR);
        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());

        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.REOPEN, null, EDITOR))));
        transition(Action.REOPEN, null, CURATOR);
        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
    }

    @Test
    void assignmentRequiringReviewLimitsMarkDoneToCurators() {
        progress(task(Task.TaskStatus.OPEN, Task.PageCompletion.REVIEW), false);

        assertEquals(403, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.MARK_DONE, null, EDITOR))));
    }

    @Test
    void reopenSkipsTheLeaseCheck() {
        page.setWorkflowState(Page.WorkflowState.DONE);

        transition(Action.REOPEN, null, CURATOR);

        verify(annotationLeaseService, never()).assertNoOtherActiveEditor(any(), any());
    }

    @Test
    void transitionFromTheWrongStateConflicts() {
        page.setWorkflowState(Page.WorkflowState.DONE);

        assertEquals(409, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.SUBMIT, null, EDITOR))));
        assertEquals(409, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.APPROVE, null, CURATOR))));
    }

    @Test
    void assignmentSyncCannotBeRequested() {
        assertEquals(400, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.ASSIGNMENT_SYNC, null, CURATOR))));
    }

    @Test
    void transientActionLockRejectsTransitions() {
        page.setLocked(true);
        page.setLockedReason("LAREX Action running: OCR");

        assertEquals(423, statusOf(assertThrows(ResponseStatusException.class, () -> transition(Action.SUBMIT, null, EDITOR))));
    }

    @Test
    void recomputeKeepsStateWithoutParticipatingAssignments() {
        page.setWorkflowState(Page.WorkflowState.DONE);

        List<Page> updated = service.recomputeForPageIds(List.of("page-1"));

        assertEquals(List.of(page), updated);
        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
        verify(eventRepository, never()).saveAll(any());
    }

    @Test
    void recomputeReturnsPagesWithOpenSubtasksToOpen() {
        page.setWorkflowState(Page.WorkflowState.DONE);
        progress(task(Task.TaskStatus.OPEN, Task.PageCompletion.DONE), false);

        service.recomputeForPageIds(List.of("page-1"));

        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
        verify(annotationLeaseService, never()).assertNoOtherActiveEditor(any(), any());
    }

    @Test
    void recomputeMarksFinishedPagesDoneWithoutRequiredReview() {
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.DONE), true);

        service.recomputeForPageIds(List.of("page-1"), EDITOR);

        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
        verify(annotationLeaseService).assertNoOtherActiveEditor("page-1", EDITOR);
    }

    @Test
    void recomputeSendsFinishedPagesToReviewForAReviewAssignment() {
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.REVIEW), true);

        service.recomputeForPageIds(List.of("page-1"), EDITOR);

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
        assertEquals(EDITOR, page.getReviewSubmittedByUserId());
        verify(annotationLeaseService).assertNoOtherActiveEditor("page-1", EDITOR);
    }

    @Test
    void projectPolicyUpgradesDoneAssignmentsToReview() {
        project.setReviewRequired(true);
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.DONE), true);

        service.recomputeForPageIds(List.of("page-1"), EDITOR);

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
    }

    @Test
    void recomputeDoesNotUndoAnApproval() {
        page.setWorkflowState(Page.WorkflowState.DONE);
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.REVIEW), true);

        service.recomputeForPageIds(List.of("page-1"), EDITOR);

        assertEquals(Page.WorkflowState.DONE, page.getWorkflowState());
    }

    @Test
    void recomputeLeavesPagesInReview() {
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId(EDITOR);
        progress(task(Task.TaskStatus.IN_PROGRESS, Task.PageCompletion.REVIEW), true);

        service.recomputeForPageIds(List.of("page-1"), CURATOR);

        assertEquals(Page.WorkflowState.IN_REVIEW, page.getWorkflowState());
        assertEquals(EDITOR, page.getReviewSubmittedByUserId());
    }

    @Test
    void cancelledAssignmentsDoNotParticipate() {
        progress(task(Task.TaskStatus.CANCELLED, Task.PageCompletion.REVIEW), true);

        service.recomputeForPageIds(List.of("page-1"));

        assertEquals(Page.WorkflowState.OPEN, page.getWorkflowState());
    }

    @Test
    void deletionRecomputeIgnoresPagesThatNoLongerExist() {
        when(pageRepository.findAllByIdInForUpdate(List.of("page-1", "deleted-page"))).thenReturn(List.of(page));

        List<Page> updated = service.recomputeForExistingPageIds(List.of("page-1", "deleted-page"));

        assertEquals(List.of(page), updated);
    }

    @Test
    void oldPackagesWithInProgressPagesStayImportable() throws Exception {
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();

        assertEquals(Page.WorkflowState.OPEN, mapper.readValue("\"IN_PROGRESS\"", Page.WorkflowState.class));
    }

    private void progress(Task task, boolean completed) {
        when(taskPageLinkRepository.findPageStateTaskSubtasksByPageIds(List.of("page-1")))
                .thenReturn(List.<Object[]>of(new Object[]{"page-1", task, completed}));
    }

    private Task task(Task.TaskStatus status, Task.PageCompletion pageCompletion) {
        Task task = new Task("Task", null, CURATOR, Task.TaskPriority.MEDIUM, WORKSPACE);
        task.setStatus(status);
        task.setPageCompletion(pageCompletion);
        return task;
    }
}
