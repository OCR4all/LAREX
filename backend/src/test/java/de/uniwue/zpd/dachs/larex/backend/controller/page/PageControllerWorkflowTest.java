package de.uniwue.zpd.dachs.larex.backend.controller.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.dto.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageWorkflowEvent.Action;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageTextConfidenceStatsService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageIndexStatusReadService;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageControllerWorkflowTest {

    private static final String PROJECT = "project-1";

    @Mock PageService pageService;
    @Mock(answer = Answers.RETURNS_DEFAULTS) PageIndexStatusReadService pageIndexStatusReadService;
    @Mock PageTextConfidenceStatsService pageTextConfidenceStatsService;
    @Mock de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService tagLookupService;
    @Mock PageWorkflowService pageWorkflowService;
    @Mock PageFilterIndexService pageFilterIndexService;
    @Mock PageOrderService pageOrderService;

    private PageController controller;
    private Page page;

    @BeforeEach
    void setUp() {
        controller = new PageController(pageService, null, pageFilterIndexService, pageIndexStatusReadService,
                tagLookupService, null, null, null, null, null, pageOrderService,
                pageTextConfidenceStatsService, pageWorkflowService, null);
        Project project = new Project();
        project.setId(PROJECT);
        page = new Page("Page", null, project);
        page.setId("page-1");
        page.setWorkflowState(Page.WorkflowState.IN_REVIEW);
        page.setReviewSubmittedByUserId("editor-1");
    }

    private PageDto.Response transition(String userId) {
        var request = new PageDto.WorkflowTransitionRequest(List.of("page-1"), Action.SUBMIT, "Please check");
        when(pageWorkflowService.transition(PROJECT, List.of("page-1"), Action.SUBMIT, "Please check", userId))
                .thenReturn(List.of(page));
        return controller.transitionWorkflow(PROJECT, request, userId).getBody().getFirst();
    }

    @Test
    void transitionReportsTheLockForEditors() {
        PageDto.Response response = transition("editor-1");

        assertThat(response.workflowState()).isEqualTo(Page.WorkflowState.IN_REVIEW);
        assertThat(response.locked()).isTrue();
        assertThat(response.lockedReason()).isEqualTo("Page is in review");
        assertThat(response.reviewSubmittedByUserId()).isEqualTo("editor-1");
    }

    @Test
    void transitionKeepsPagesInReviewUnlockedForReviewers() {
        when(pageService.canReviewPages(PROJECT, "curator-1")).thenReturn(true);

        PageDto.Response response = transition("curator-1");

        assertThat(response.locked()).isFalse();
        assertThat(response.lockedReason()).isNull();
    }

    @Test
    void summariesApplyThePerUserLock() {
        when(pageService.getProjectPageSummaries(PROJECT, "curator-1")).thenReturn(List.of(page));
        when(pageService.canReviewPages(PROJECT, "curator-1")).thenReturn(true);

        PageDto.Summary summary = controller.getPageSummaries(PROJECT, "curator-1").getBody().getFirst();

        assertThat(summary.locked()).isFalse();
        assertThat(summary.changesRequested()).isFalse();
    }

    @Test
    void workflowEventsComeFromTheService() {
        var event = new PageDto.WorkflowEventResponse("event-1", Action.APPROVE, Page.WorkflowState.IN_REVIEW,
                Page.WorkflowState.DONE, "curator-1", null, null, LocalDateTime.now());
        when(pageWorkflowService.listEvents(PROJECT, "page-1", "editor-1")).thenReturn(List.of(event));

        assertThat(controller.getWorkflowEvents(PROJECT, "page-1", "editor-1").getBody()).containsExactly(event);
    }

    @Test
    void transitionRequestsAreLimited() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var tooLong = new PageDto.WorkflowTransitionRequest(List.of("page-1"), Action.SUBMIT, "x".repeat(2001));
            var empty = new PageDto.WorkflowTransitionRequest(List.of(), Action.SUBMIT, null);
            var noAction = new PageDto.WorkflowTransitionRequest(List.of("page-1"), null, null);

            assertThat(validator.validate(tooLong)).hasSize(1);
            assertThat(validator.validate(empty)).hasSize(1);
            assertThat(validator.validate(noAction)).hasSize(1);
        }
    }
}
