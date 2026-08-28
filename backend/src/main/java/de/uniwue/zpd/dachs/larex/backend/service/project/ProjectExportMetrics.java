package de.uniwue.zpd.dachs.larex.backend.service.project;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectExportJob;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectExportJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ProjectExportMetrics {
    private final MeterRegistry registry;
    private final DistributionSummary artifactBytes;

    public ProjectExportMetrics(MeterRegistry registry, ProjectExportJobRepository repository) {
        this.registry = registry;
        this.artifactBytes = DistributionSummary.builder("larex.project.export.artifact.bytes")
                .baseUnit("bytes")
                .description("Sizes of completed project export artifacts")
                .register(registry);
        Gauge.builder("larex.project.export.queue.depth", repository,
                        value -> value.countByStatus(ProjectExportJob.Status.QUEUED))
                .description("Number of queued project export jobs")
                .register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void finish(Timer.Sample sample, String outcome, long bytes) {
        Counter.builder("larex.project.export.jobs")
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        sample.stop(Timer.builder("larex.project.export.duration")
                .tag("outcome", outcome)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofSeconds(1))
                .register(registry));
        if (bytes >= 0) artifactBytes.record(bytes);
    }
}
