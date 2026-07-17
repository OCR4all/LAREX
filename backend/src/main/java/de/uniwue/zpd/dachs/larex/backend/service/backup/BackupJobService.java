package de.uniwue.zpd.dachs.larex.backend.service.backup;

import de.uniwue.zpd.dachs.larex.backend.dto.BackupJobDto;
import de.uniwue.zpd.dachs.larex.backend.config.BackupProperties;
import de.uniwue.zpd.dachs.larex.backend.service.notification.JobRealtimePublisher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Service
public class BackupJobService {

    private static final Logger log = LoggerFactory.getLogger(BackupJobService.class);

    private final BackupJobProcessor backupJobProcessor;
    private final AsyncTaskExecutor taskExecutor;
    private final BackupProperties properties;
    private final JobRealtimePublisher jobRealtimePublisher;

    private final Map<String, BackupJobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> futures = new ConcurrentHashMap<>();
    private final List<Path> allowedPaths = new ArrayList<>();

    public BackupJobService(@Lazy BackupJobProcessor backupJobProcessor,
                            @Qualifier("importTaskExecutor") AsyncTaskExecutor taskExecutor,
                            BackupProperties properties,
                            JobRealtimePublisher jobRealtimePublisher) {
        this.backupJobProcessor = backupJobProcessor;
        this.taskExecutor = taskExecutor;
        this.properties = properties;
        this.jobRealtimePublisher = jobRealtimePublisher;
    }

    @PostConstruct
    void initAllowedPaths() {
        allowedPaths.clear();

        if (properties.getAllowedPaths() != null) {
            for (String raw : properties.getAllowedPaths()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                try {
                    Path path = Paths.get(raw.trim()).toAbsolutePath().normalize();
                    allowedPaths.add(path);
                    log.info("Allowed backup path configured: {}", path);
                } catch (Exception e) {
                    log.warn("Invalid backup allowed path: {}", raw);
                }
            }
        }

        if (properties.getOutputDir() != null && !properties.getOutputDir().isBlank()) {
            try {
                Path outputPath = Paths.get(properties.getOutputDir()).toAbsolutePath().normalize();
                if (!allowedPaths.contains(outputPath)) {
                    allowedPaths.add(outputPath);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public BackupJobDto.ValidatePathResponse validatePath(BackupJobDto.ValidatePathRequest request) {
        if (!properties.isEnabled()) {
            return new BackupJobDto.ValidatePathResponse(false, null, "Backup features are disabled");
        }

        if (request == null || request.path() == null || request.path().isBlank()) {
            return new BackupJobDto.ValidatePathResponse(false, null, "Path is required");
        }

        try {
            Path path = Paths.get(request.path()).toAbsolutePath().normalize();
            Path checkPath = request.role() == BackupJobDto.PathRole.OUTPUT && !Files.exists(path)
                    ? path.getParent()
                    : path;

            if (checkPath == null) {
                return new BackupJobDto.ValidatePathResponse(false, null, "Invalid path");
            }

            if (!isPathAllowed(checkPath)) {
                return new BackupJobDto.ValidatePathResponse(
                        false,
                        path.toString(),
                        "Path is outside allowed directories: " + allowedPaths
                );
            }

            if (request.role() == BackupJobDto.PathRole.SOURCE) {
                if (!Files.exists(path)) {
                    return new BackupJobDto.ValidatePathResponse(false, path.toString(), "Source path does not exist");
                }
                if (!Files.isReadable(path)) {
                    return new BackupJobDto.ValidatePathResponse(false, path.toString(), "Source path is not readable");
                }
            } else {
                if (Files.exists(path) && Files.isDirectory(path) && !Files.isWritable(path)) {
                    return new BackupJobDto.ValidatePathResponse(false, path.toString(), "Output directory is not writable");
                }
                if (!Files.exists(path) && path.getParent() != null && !Files.exists(path.getParent())) {
                    return new BackupJobDto.ValidatePathResponse(false, path.toString(), "Output parent directory does not exist");
                }
            }

            return new BackupJobDto.ValidatePathResponse(true, path.toString(), null);
        } catch (Exception e) {
            return new BackupJobDto.ValidatePathResponse(false, null, "Invalid path: " + e.getMessage());
        }
    }

    public BackupJobDto.JobResponse createJob(String userId, BackupJobDto.CreateJobRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("Backup features are disabled");
        }

        if (request == null || request.type() == null) {
            throw new IllegalArgumentException("Job type is required");
        }

        BackupJobDto.ValidatePathResponse outputValidation = validatePath(
                new BackupJobDto.ValidatePathRequest(request.outputPath(), BackupJobDto.PathRole.OUTPUT)
        );
        if (!outputValidation.valid()) {
            throw new IllegalArgumentException(outputValidation.errorMessage());
        }

        String normalizedSource = null;
        if (request.type() == BackupJobDto.JobType.RESEED) {
            BackupJobDto.ValidatePathResponse sourceValidation = validatePath(
                    new BackupJobDto.ValidatePathRequest(request.sourcePath(), BackupJobDto.PathRole.SOURCE)
            );
            if (!sourceValidation.valid()) {
                throw new IllegalArgumentException(sourceValidation.errorMessage());
            }
            normalizedSource = sourceValidation.normalizedPath();
        }

        String id = UUID.randomUUID().toString();
        BackupJobState state = BackupJobState.pending(
                id,
                request.type(),
                normalizedSource,
                outputValidation.normalizedPath(),
                userId
        );
        jobs.put(id, state);

        final String sourcePathFinal = normalizedSource;
        Future<?> future = taskExecutor.submit(() -> backupJobProcessor.processJob(id, userId, request, sourcePathFinal));
        futures.put(id, future);
        publishUpdate(state);

        return toJobResponse(state);
    }

    public List<BackupJobDto.JobSummary> listJobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(BackupJobState::created).reversed())
                .map(this::toJobSummary)
                .toList();
    }

    public BackupJobDto.JobResponse getJob(String jobId) {
        BackupJobState state = jobs.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Backup job not found: " + jobId);
        }
        return toJobResponse(state);
    }

    public void cancelJob(String jobId) {
        BackupJobState state = jobs.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Backup job not found: " + jobId);
        }

        if (state.status() == BackupJobDto.JobStatus.COMPLETED
                || state.status() == BackupJobDto.JobStatus.FAILED
                || state.status() == BackupJobDto.JobStatus.CANCELLED) {
            throw new IllegalArgumentException("Job cannot be cancelled in current state: " + state.status());
        }

        state.setStatus(BackupJobDto.JobStatus.CANCELLED);
        state.setUpdated(LocalDateTime.now());
        state.setCompletedAt(LocalDateTime.now());

        Future<?> future = futures.get(jobId);
        if (future != null) {
            future.cancel(true);
        }
        publishUpdate(state);
    }

    public boolean isCancelled(String jobId) {
        BackupJobState state = jobs.get(jobId);
        return state != null && state.status() == BackupJobDto.JobStatus.CANCELLED;
    }

    public void markRunning(String jobId, long totalItems, String step) {
        BackupJobState state = requiredState(jobId);
        state.setStatus(BackupJobDto.JobStatus.RUNNING);
        state.setTotalItems(totalItems);
        state.setCurrentStep(step);
        state.setUpdated(LocalDateTime.now());
        publishUpdate(state);
    }

    public void updateProgress(String jobId, long processedItems, long totalItems, String step) {
        BackupJobState state = requiredState(jobId);
        int previousPercent = state.progressPercent();
        state.setProcessedItems(processedItems);
        state.setTotalItems(totalItems);
        state.setProgressPercent(computeProgressPercent(processedItems, totalItems));
        state.setCurrentStep(step);
        state.setUpdated(LocalDateTime.now());
        if (state.progressPercent() != previousPercent) {
            publishUpdate(state);
        }
    }

    public void addWarning(String jobId, String warning) {
        if (warning == null || warning.isBlank()) {
            return;
        }
        BackupJobState state = requiredState(jobId);
        state.warnings().add(warning);
        state.setUpdated(LocalDateTime.now());
        publishUpdate(state);
    }

    public void markCompleted(String jobId, String resultPath) {
        BackupJobState state = requiredState(jobId);
        state.setStatus(BackupJobDto.JobStatus.COMPLETED);
        state.setProgressPercent(100);
        state.setResultPath(resultPath);
        state.setCompletedAt(LocalDateTime.now());
        state.setUpdated(LocalDateTime.now());
        futures.remove(jobId);
        publishUpdate(state);
    }

    public void markFailed(String jobId, String errorMessage) {
        BackupJobState state = requiredState(jobId);
        if (state.status() == BackupJobDto.JobStatus.CANCELLED) {
            futures.remove(jobId);
            return;
        }

        state.setStatus(BackupJobDto.JobStatus.FAILED);
        state.setErrorMessage(errorMessage);
        state.setCompletedAt(LocalDateTime.now());
        state.setUpdated(LocalDateTime.now());
        futures.remove(jobId);
        publishUpdate(state);
    }

    public int getMaxFilesPerJob() {
        return properties.getMaxFilesPerJob();
    }

    public String getOutputDir() {
        return properties.getOutputDir();
    }

    private BackupJobState requiredState(String jobId) {
        BackupJobState state = jobs.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Backup job not found: " + jobId);
        }
        return state;
    }

    private void publishUpdate(BackupJobState state) {
        jobRealtimePublisher.publish(
                "BACKUP",
                state.id(),
                null,
                null,
                state.status().name(),
                null
        );
    }

    private int computeProgressPercent(long processedItems, long totalItems) {
        if (totalItems <= 0) {
            return 0;
        }
        return (int) Math.min(100L, Math.max(0L, (processedItems * 100L) / totalItems));
    }

    private boolean isPathAllowed(Path path) {
        return allowedPaths.stream().anyMatch(path::startsWith);
    }

    private BackupJobDto.JobSummary toJobSummary(BackupJobState state) {
        return new BackupJobDto.JobSummary(
                state.id(),
                state.type(),
                state.status(),
                state.progressPercent(),
                state.currentStep(),
                state.created(),
                state.completedAt()
        );
    }

    private BackupJobDto.JobResponse toJobResponse(BackupJobState state) {
        return new BackupJobDto.JobResponse(
                state.id(),
                state.type(),
                state.status(),
                state.sourcePath(),
                state.outputPath(),
                state.progressPercent(),
                state.processedItems(),
                state.totalItems(),
                state.currentStep(),
                state.errorMessage(),
                state.resultPath(),
                List.copyOf(state.warnings()),
                state.created(),
                state.updated(),
                state.completedAt()
        );
    }

    private static final class BackupJobState {
        private final String id;
        private final BackupJobDto.JobType type;
        private final String sourcePath;
        private final String outputPath;
        private final String createdBy;
        private volatile BackupJobDto.JobStatus status;
        private volatile int progressPercent;
        private volatile long processedItems;
        private volatile long totalItems;
        private volatile String currentStep;
        private volatile String errorMessage;
        private volatile String resultPath;
        private final List<String> warnings;
        private final LocalDateTime created;
        private volatile LocalDateTime updated;
        private volatile LocalDateTime completedAt;

        private BackupJobState(String id,
                               BackupJobDto.JobType type,
                               String sourcePath,
                               String outputPath,
                               String createdBy,
                               BackupJobDto.JobStatus status,
                               LocalDateTime created,
                               LocalDateTime updated) {
            this.id = id;
            this.type = type;
            this.sourcePath = sourcePath;
            this.outputPath = outputPath;
            this.createdBy = createdBy;
            this.status = status;
            this.created = created;
            this.updated = updated;
            this.warnings = new ArrayList<>();
        }

        static BackupJobState pending(String id,
                                      BackupJobDto.JobType type,
                                      String sourcePath,
                                      String outputPath,
                                      String createdBy) {
            LocalDateTime now = LocalDateTime.now();
            return new BackupJobState(id, type, sourcePath, outputPath, createdBy, BackupJobDto.JobStatus.PENDING, now, now);
        }

        String id() { return id; }
        BackupJobDto.JobType type() { return type; }
        String sourcePath() { return sourcePath; }
        String outputPath() { return outputPath; }
        String createdBy() { return createdBy; }
        BackupJobDto.JobStatus status() { return status; }
        int progressPercent() { return progressPercent; }
        long processedItems() { return processedItems; }
        long totalItems() { return totalItems; }
        String currentStep() { return currentStep; }
        String errorMessage() { return errorMessage; }
        String resultPath() { return resultPath; }
        List<String> warnings() { return warnings; }
        LocalDateTime created() { return created; }
        LocalDateTime updated() { return updated; }
        LocalDateTime completedAt() { return completedAt; }

        void setStatus(BackupJobDto.JobStatus status) { this.status = status; }
        void setProgressPercent(int progressPercent) { this.progressPercent = progressPercent; }
        void setProcessedItems(long processedItems) { this.processedItems = processedItems; }
        void setTotalItems(long totalItems) { this.totalItems = totalItems; }
        void setCurrentStep(String currentStep) { this.currentStep = currentStep; }
        void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        void setResultPath(String resultPath) { this.resultPath = resultPath; }
        void setUpdated(LocalDateTime updated) { this.updated = updated; }
        void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    }
}
