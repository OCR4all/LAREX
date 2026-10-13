package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.PageOutcome;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.PageOutcomeStatus;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.ReportCounts;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.RunIssue;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto.RunReport;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunPageResult;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunPageResultRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns outcome accounting; file summaries and evaluation metrics are independent. */
@Service
public class ActionRunReportService {
    private final ActionRunPageResultRepository pages;
    private final PageRepository pageRepository;
    private final ActionRunPayloadService payload;
    private final ObjectMapper mapper;

    public ActionRunReportService(ActionRunPageResultRepository pages, PageRepository pageRepository,
                                  ActionRunPayloadService payload, ObjectMapper mapper) {
        this.pages = pages;
        this.pageRepository = pageRepository;
        this.payload = payload;
        this.mapper = mapper;
    }

    public PageOutcome validateOutcome(PageOutcome outcome, String pageId) {
        if (outcome == null) return new PageOutcome(pageId, null, PageOutcomeStatus.SUCCEEDED, null, List.of());
        if (!pageId.equals(outcome.pageId()) || outcome.status() == null
                || !Set.of(PageOutcomeStatus.SUCCEEDED, PageOutcomeStatus.FAILED, PageOutcomeStatus.SKIPPED).contains(outcome.status())) {
            throw new IllegalArgumentException("Page outcome requires a matching pageId and SUCCEEDED, FAILED, or SKIPPED status");
        }
        if (outcome.status() != PageOutcomeStatus.SUCCEEDED
                && (outcome.message() == null || outcome.message().isBlank())) {
            throw new IllegalArgumentException("Failed and skipped pages require a reason");
        }
        return new PageOutcome(pageId, null, outcome.status(), safe(outcome.message()),
                outcome.warnings().stream().map(this::safeRequired).toList());
    }

    public RunReport validateReport(ActionRun run, RunReport report, boolean incremental) {
        if (report == null) return null;
        if (report.schemaVersion() != 1) throw new IllegalArgumentException("Unsupported run report schemaVersion");
        if (mapper.writeValueAsBytes(report).length > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Run report must not exceed 10 MiB");
        }
        List<PageOutcome> outcomes = new ArrayList<>();
        Set<String> selected = Set.copyOf(payload.readPageIds(run));
        if (run.getKind() != ActionRun.Kind.PROCESSING && !report.pageOutcomes().isEmpty()) {
            throw new IllegalArgumentException("Only processing Actions can report page outcomes");
        }
        if (incremental && !report.pageOutcomes().isEmpty()) {
            throw new IllegalArgumentException("Incremental completion uses the previously submitted page outcomes");
        }
        Set<String> seen = new java.util.HashSet<>();
        for (PageOutcome outcome : report.pageOutcomes()) {
            if (outcome == null || outcome.pageId() == null || !selected.contains(outcome.pageId()) || !seen.add(outcome.pageId())) {
                throw new IllegalArgumentException("Run report page IDs must be unique and belong to the run");
            }
            outcomes.add(validateOutcome(outcome, outcome.pageId()));
        }
        if (!incremental && run.getKind() == ActionRun.Kind.PROCESSING && !seen.equals(selected)) {
            throw new IllegalArgumentException("Bulk run reports must account for every selected page");
        }
        List<RunIssue> issues = report.issues().stream().map(issue -> {
            if (issue == null || issue.severity() == null || !Set.of("WARNING", "ERROR").contains(issue.severity())) {
                throw new IllegalArgumentException("Run issue severity must be WARNING or ERROR");
            }
            return new RunIssue(issue.severity(), safeRequired(issue.message()));
        }).toList();
        return new RunReport(1, outcomes, issues, null);
    }

    public void setIssues(ActionRun run, RunReport report) {
        if (report != null) run.setRunReportJson(payload.writeJson(new RunReport(1, List.of(), report.issues(), null)));
    }

    public void record(ActionRun run, PageOutcome outcome, List<Map<String, Object>> files) {
        record(run, outcome, files, false);
    }

    public void record(ActionRun run, PageOutcome outcome, List<Map<String, Object>> files, boolean rejected) {
        ActionRunPageResult result = new ActionRunPageResult();
        result.setRun(run);
        result.setPageId(outcome.pageId());
        result.setOutcomeJson(payload.writeJson(outcome));
        result.setValidationRejected(rejected);
        result.setResultSummaryJson(payload.writeJson(files));
        pages.saveAndFlush(result);
    }

    public PageOutcome outcome(ActionRunPageResult result) {
        return result.getOutcomeJson() == null
                ? new PageOutcome(result.getPageId(), null, PageOutcomeStatus.UNKNOWN, "Historical outcome unavailable", List.of())
                : mapper.readValue(result.getOutcomeJson(), PageOutcome.class);
    }

    private RunReport stored(ActionRun run) {
        return run.getRunReportJson() == null ? new RunReport(1, List.of(), List.of(), null)
                : mapper.readValue(run.getRunReportJson(), RunReport.class);
    }

    public ReportCounts summary(ActionRun run) {
        return stored(run).counts();
    }

    public void refresh(ActionRun run) {
        RunReport report = report(run, false);
        run.setRunReportJson(payload.writeJson(new RunReport(1, List.of(), report.issues(), report.counts())));
    }

    public RunReport report(ActionRun run, boolean names) {
        List<String> ids = run.getKind() == ActionRun.Kind.PROCESSING ? payload.readPageIds(run) : List.of();
        Map<String, PageOutcome> recorded = pages.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                .map(this::outcome).collect(Collectors.toMap(PageOutcome::pageId, Function.identity()));
        Map<String, String> labels = new LinkedHashMap<>();
        if (names && !ids.isEmpty()) {
            pageRepository.findAllByIdIn(ids).forEach(page -> labels.put(page.getId(), page.getName()));
        }
        List<PageOutcome> outcomes = ids.stream().map(id -> {
            PageOutcome value = recorded.getOrDefault(id, new PageOutcome(id, null,
                    run.getRunReportJson() == null && run.getStatus().isCompleted()
                            ? PageOutcomeStatus.UNKNOWN : PageOutcomeStatus.UNFINISHED, null, List.of()));
            return new PageOutcome(id, labels.get(id), value.status(), value.message(), value.warnings());
        }).toList();
        int succeeded = 0, failed = 0, skipped = 0, unfinished = 0, unknown = 0;
        for (PageOutcome value : outcomes) {
            switch (value.status()) {
                case SUCCEEDED -> succeeded++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
                case UNFINISHED -> unfinished++;
                case UNKNOWN -> unknown++;
            }
        }
        List<RunIssue> issues = new ArrayList<>(stored(run).issues());
        if (run.getStatus() == ActionRun.Status.FAILED && run.getErrorMessage() != null && !run.getErrorMessage().isBlank()
                && issues.stream().noneMatch(issue -> issue.message().equals(safe(run.getErrorMessage())))) {
            issues.add(new RunIssue("ERROR", safe(run.getErrorMessage())));
        }
        return new RunReport(1, outcomes, issues,
                new ReportCounts(ids.size(), succeeded, failed, skipped, unfinished, unknown));
    }

    public ActionRun.Status completionStatus(ActionRun run) {
        RunReport report = report(run, false);
        ReportCounts counts = report.counts();
        if (counts.failed() > 0 && counts.succeeded() == 0 && counts.skipped() == 0 && counts.unknown() == 0) {
            return ActionRun.Status.FAILED;
        }
        boolean issues = counts.failed() > 0 || !report.issues().isEmpty()
                || report.pageOutcomes().stream().anyMatch(page -> !page.warnings().isEmpty());
        return issues ? ActionRun.Status.COMPLETED_WITH_ISSUES : ActionRun.Status.COMPLETED;
    }

    public List<String> retryablePageIds(ActionRun run) {
        Set<String> settled = pages.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                .filter(page -> outcome(page).status() != PageOutcomeStatus.FAILED)
                .map(ActionRunPageResult::getPageId).collect(Collectors.toSet());
        return payload.readPageIds(run).stream().filter(id -> !settled.contains(id)).toList();
    }

    private String safeRequired(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Report messages must not be blank");
        return safe(value);
    }

    private String safe(String value) {
        String safe = ActionRunService.redactProcessorSecrets(value);
        return safe == null || safe.length() <= 4000 ? safe : safe.substring(0, 4000);
    }
}
