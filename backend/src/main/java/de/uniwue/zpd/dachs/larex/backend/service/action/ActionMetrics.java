package de.uniwue.zpd.dachs.larex.backend.service.action;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class ActionMetrics {

    private final Counter pageImports;
    private final Counter duplicatePageSubmissions;
    private final Counter terminalResultReplays;
    private final Counter notificationFailures;
    private final Timer pageImportDuration;
    private final DistributionSummary pageResultFiles;

    public ActionMetrics(MeterRegistry registry) {
        pageImports = registry.counter("larex.actions.page.imports");
        duplicatePageSubmissions = registry.counter("larex.actions.page.duplicate_submissions");
        terminalResultReplays = registry.counter("larex.actions.result.terminal_replays");
        notificationFailures = registry.counter("larex.actions.notification.failures");
        pageImportDuration = registry.timer("larex.actions.page.import.duration");
        pageResultFiles = DistributionSummary.builder("larex.actions.page.result.files")
                .baseUnit("files")
                .register(registry);
    }

    public void recordPageImport(long startedNanos, int resultFileCount) {
        pageImports.increment();
        pageImportDuration.record(Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)));
        pageResultFiles.record(resultFileCount);
    }

    public void recordDuplicatePageSubmission() {
        duplicatePageSubmissions.increment();
    }

    public void recordTerminalResultReplay() {
        terminalResultReplays.increment();
    }

    public void recordNotificationFailure() {
        notificationFailures.increment();
    }
}
