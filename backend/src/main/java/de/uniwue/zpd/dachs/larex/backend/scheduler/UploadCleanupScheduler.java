package de.uniwue.zpd.dachs.larex.backend.scheduler;

import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.ImportJobStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.repository.ImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.ChunkedUploadService;
import de.uniwue.zpd.dachs.larex.backend.service.HierarchicalFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Component
public class UploadCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(UploadCleanupScheduler.class);

    private final UploadSessionRepository sessionRepository;
    private final ImportJobRepository importJobRepository;
    private final ChunkedUploadService chunkedUploadService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;

    @Value("${larex.upload.session-timeout-hours:24}")
    private int sessionTimeoutHours;

    @Value("${larex.upload.temp-directory:/uploads/temp}")
    private String tempDirectory;

    public UploadCleanupScheduler(UploadSessionRepository sessionRepository,
                                   ImportJobRepository importJobRepository,
                                   ChunkedUploadService chunkedUploadService,
                                   HierarchicalFileStorageService hierarchicalFileStorageService) {
        this.sessionRepository = sessionRepository;
        this.importJobRepository = importJobRepository;
        this.chunkedUploadService = chunkedUploadService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
    }

    /**
     * Clean up stale upload sessions every hour.
     * Sessions that have been in PENDING or UPLOADING state for longer than the timeout are cleaned up.
     */
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupStaleSessions() {
        log.info("Starting cleanup of stale upload sessions");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(sessionTimeoutHours);

        List<UploadSessionStatus> staleStatuses = List.of(
                UploadSessionStatus.PENDING,
                UploadSessionStatus.UPLOADING
        );

        List<UploadSession> staleSessions = sessionRepository.findStaleSessionsCreatedBefore(staleStatuses, cutoff);

        int cleanedCount = 0;
        for (UploadSession session : staleSessions) {
            try {
                log.info("Cleaning up stale upload session: {} (created: {})", session.getId(), session.getCreated());

                // Clean up temp files
                chunkedUploadService.cleanupSessionTempFiles(session.getId());

                // Mark session as cancelled
                session.setStatus(UploadSessionStatus.CANCELLED);
                session.setErrorMessage("Session timed out after " + sessionTimeoutHours + " hours");
                sessionRepository.save(session);

                cleanedCount++;
            } catch (Exception e) {
                log.error("Failed to clean up stale session: {}", session.getId(), e);
            }
        }

        if (cleanedCount > 0) {
            log.info("Cleaned up {} stale upload sessions", cleanedCount);
        }
    }

    /**
     * Clean up orphaned temp directories every 6 hours.
     * Directories in the temp folder that don't have corresponding sessions are cleaned up.
     */
    @Scheduled(fixedRate = 21600000) // Run every 6 hours
    public void cleanupOrphanedTempDirectories() {
        log.info("Starting cleanup of orphaned temp directories");

        try {
            Path tempPath = Paths.get(tempDirectory).toAbsolutePath();
            if (!Files.exists(tempPath) || !Files.isDirectory(tempPath)) {
                return;
            }

            int cleanedCount = 0;
            try (Stream<Path> dirs = Files.list(tempPath).filter(Files::isDirectory)) {
                for (Path dir : dirs.toList()) {
                    String dirName = dir.getFileName().toString();

                    // Check if session exists
                    boolean sessionExists = sessionRepository.findById(dirName).isPresent();

                    if (!sessionExists) {
                        log.info("Cleaning up orphaned temp directory: {}", dirName);
                        deleteDirectory(dir);
                        cleanedCount++;
                    }
                }
            }

            if (cleanedCount > 0) {
                log.info("Cleaned up {} orphaned temp directories", cleanedCount);
            }

        } catch (IOException e) {
            log.error("Failed to clean up orphaned temp directories", e);
        }
    }

    /**
     * Clean up old completed/failed upload sessions every day.
     * Sessions older than 7 days that are completed or failed are deleted.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Run at 3 AM every day
    @Transactional
    public void cleanupOldSessions() {
        log.info("Starting cleanup of old upload sessions");

        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        List<UploadSessionStatus> completedStatuses = List.of(
                UploadSessionStatus.COMPLETED,
                UploadSessionStatus.FAILED,
                UploadSessionStatus.CANCELLED
        );

        int deleted = sessionRepository.deleteStaleSessionsCreatedBefore(completedStatuses, cutoff);

        if (deleted > 0) {
            log.info("Deleted {} old upload sessions", deleted);
        }
    }

    /**
     * Clean up stuck import jobs every 6 hours.
     * Jobs that have been running for longer than 24 hours are marked as failed.
     */
    @Scheduled(fixedRate = 21600000) // Run every 6 hours
    @Transactional
    public void cleanupStuckImportJobs() {
        log.info("Starting cleanup of stuck import jobs");

        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        List<ImportJobStatus> runningStatuses = List.of(
                ImportJobStatus.SCANNING,
                ImportJobStatus.VALIDATING,
                ImportJobStatus.IMPORTING
        );

        List<ImportJob> stuckJobs = importJobRepository.findStaleJobsCreatedBefore(runningStatuses, cutoff);

        int cleanedCount = 0;
        for (ImportJob job : stuckJobs) {
            try {
                log.warn("Marking stuck import job as failed: {} (created: {})", job.getId(), job.getCreated());

                job.setStatus(ImportJobStatus.FAILED);
                job.setErrorMessage("Job timed out after 24 hours");
                job.setCompletedAt(LocalDateTime.now());
                importJobRepository.save(job);

                cleanedCount++;
            } catch (Exception e) {
                log.error("Failed to clean up stuck import job: {}", job.getId(), e);
            }
        }

        if (cleanedCount > 0) {
            log.info("Marked {} stuck import jobs as failed", cleanedCount);
        }
    }

    /**
     * Clean up empty sharding directories under /ws every day.
     */
    @Scheduled(cron = "0 30 3 * * ?") // Run at 3:30 AM every day
    public void cleanupEmptyShardDirectories() {
        int deleted = hierarchicalFileStorageService.cleanupEmptyWorkspaceDirectories();
        if (deleted > 0) {
            log.info("Deleted {} empty shard directories", deleted);
        }
    }

    private void deleteDirectory(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete: {}", path, e);
                        }
                    });
        }
    }
}
