package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.*;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunPageResult;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunPageResultRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActionRunReportServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ActionRunPayloadService payload = new ActionRunPayloadService(mapper);
    private final ActionRunPageResultRepository pages = mock(ActionRunPageResultRepository.class);
    private final PageRepository pageRepository = mock(PageRepository.class);
    private final ActionRunReportService reports = new ActionRunReportService(pages, pageRepository, payload, mapper);
    private final ActionRun run = new ActionRun();
    private final List<ActionRunPageResult> results = new ArrayList<>();

    @BeforeEach
    void setup() {
        run.setId("run");
        run.setPageIdsJson("[\"p1\",\"p2\",\"p3\"]");
        when(pages.findByRunIdOrderByCreatedAsc("run")).thenAnswer(ignored -> List.copyOf(results));
        when(pages.saveAndFlush(any())).thenAnswer(invocation -> {
            results.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    private void outcome(String id, PageOutcomeStatus status, List<String> warnings) {
        reports.record(run, new PageOutcome(id, null, status, status == PageOutcomeStatus.FAILED ? "Page error" : null, warnings), List.of());
    }

    @Test
    void thousandPagesWithTwoFailuresRetainsExactCountsAndRetrySelection() {
        run.setPageIdsJson(payload.writeJson(IntStream.range(0, 1000).mapToObj(index -> "p" + index).toList()));
        for (int i = 0; i < 1000; i++) outcome("p" + i, i >= 998 ? PageOutcomeStatus.FAILED : PageOutcomeStatus.SUCCEEDED, List.of());
        reports.refresh(run);
        assertThat(reports.summary(run)).isEqualTo(new ReportCounts(1000, 998, 2, 0, 0, 0));
        assertThat(reports.completionStatus(run)).isEqualTo(ActionRun.Status.COMPLETED_WITH_ISSUES);
        assertThat(reports.retryablePageIds(run)).containsExactly("p998", "p999");
        assertThat(mapper.readTree(run.getRunReportJson()).get("pageOutcomes").size()).isZero();
    }

    @Test
    void ordinarySkipsCompleteNormallyButWarningsCauseIssues() {
        outcome("p1", PageOutcomeStatus.SUCCEEDED, List.of());
        outcome("p2", PageOutcomeStatus.SKIPPED, List.of());
        outcome("p3", PageOutcomeStatus.SKIPPED, List.of());
        assertThat(reports.completionStatus(run)).isEqualTo(ActionRun.Status.COMPLETED);
        assertThat(reports.retryablePageIds(run)).isEmpty();
        results.clear();
        outcome("p1", PageOutcomeStatus.SUCCEEDED, List.of("Low confidence"));
        assertThat(reports.completionStatus(run)).isEqualTo(ActionRun.Status.COMPLETED_WITH_ISSUES);
    }

    @Test
    void allFailuresAreFailedAndUnsubmittedPagesRemainUnfinished() {
        outcome("p1", PageOutcomeStatus.FAILED, List.of());
        assertThat(reports.completionStatus(run)).isEqualTo(ActionRun.Status.FAILED);
        assertThat(reports.report(run, false).counts()).isEqualTo(new ReportCounts(3, 0, 1, 0, 2, 0));
        assertThat(reports.retryablePageIds(run)).containsExactly("p1", "p2", "p3");
    }

    @Test
    void historicalAcknowledgementsAreUnknownAndExcludedFromRetry() {
        ActionRunPageResult legacy = new ActionRunPageResult();
        legacy.setPageId("p1");
        results.add(legacy);
        assertThat(reports.report(run, false).counts().unknown()).isEqualTo(1);
        assertThat(reports.retryablePageIds(run)).containsExactly("p2", "p3");
        results.clear();
        assertThat(reports.retryablePageIds(run)).containsExactly("p1", "p2", "p3");
        run.setStatus(ActionRun.Status.COMPLETED);
        assertThat(reports.report(run, false).counts().unknown()).isEqualTo(3);
    }

    @Test
    void reportValidationRejectsWrongScopeDuplicatesAndIncompleteBulkCoverage() {
        for (List<PageOutcome> values : List.of(
                List.of(new PageOutcome("outside", null, PageOutcomeStatus.SUCCEEDED, null, null)),
                List.of(new PageOutcome("p1", null, PageOutcomeStatus.SUCCEEDED, null, null)),
                List.of(new PageOutcome("p1", null, PageOutcomeStatus.SUCCEEDED, null, null),
                        new PageOutcome("p1", null, PageOutcomeStatus.SUCCEEDED, null, null)))) {
            assertThatThrownBy(() -> reports.validateReport(run, new RunReport(1, values, null, null), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> reports.validateOutcome(new PageOutcome("p1", null, PageOutcomeStatus.FAILED, " ", null), "p1"))
                .hasMessageContaining("reason");
    }

    @Test
    void runLevelIssuesAreRedactedAndAvailableOnFatalRuns() {
        reports.setIssues(run, reports.validateReport(run, new RunReport(1, null,
                List.of(new RunIssue("WARNING", "Token Bearer secret-token")), null), true));
        run.setStatus(ActionRun.Status.FAILED);
        run.setErrorMessage("Model unavailable");
        RunReport report = reports.report(run, false);
        assertThat(report.issues()).extracting(RunIssue::message).containsExactly("Token Bearer [redacted]", "Model unavailable");
        assertThat(report.counts().unfinished()).isEqualTo(3);
    }
}
