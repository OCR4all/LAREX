package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.IiifProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJobItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AsyncIiifImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncIiifImportProcessor.class);
    private static final TypeReference<List<IiifJobCanvasPayload>> JOB_PAYLOAD_LIST_TYPE = new TypeReference<>() {};

    private final IiifImportJobRepository iiifImportJobRepository;
    private final IiifImportJobItemRepository iiifImportJobItemRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final ThumbnailService thumbnailService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final ObjectMapper objectMapper;
    private final IiifImageDownloader imageDownloader;
    private final IiifProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AsyncIiifImportProcessor(IiifImportJobRepository iiifImportJobRepository,
                                    IiifImportJobItemRepository iiifImportJobItemRepository,
                                    ProjectRepository projectRepository,
                                    PageRepository pageRepository,
                                    PageImageRepository pageImageRepository,
                                    HierarchicalFileStorageService hierarchicalFileStorageService,
                                    ThumbnailService thumbnailService,
                                    WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                    ObjectMapper objectMapper,
                                    IiifImageDownloader imageDownloader,
                                    IiifProperties properties,
                                    PlatformTransactionManager transactionManager) {
        this(
                iiifImportJobRepository,
                iiifImportJobItemRepository,
                projectRepository,
                pageRepository,
                pageImageRepository,
                hierarchicalFileStorageService,
                thumbnailService,
                workspaceQuotaGuardService,
                objectMapper,
                imageDownloader,
                properties,
                transactionManager,
                Clock.systemDefaultZone()
        );
    }

    AsyncIiifImportProcessor(IiifImportJobRepository iiifImportJobRepository,
                             IiifImportJobItemRepository iiifImportJobItemRepository,
                             ProjectRepository projectRepository,
                             PageRepository pageRepository,
                             PageImageRepository pageImageRepository,
                             HierarchicalFileStorageService hierarchicalFileStorageService,
                             ThumbnailService thumbnailService,
                             WorkspaceQuotaGuardService workspaceQuotaGuardService,
                             ObjectMapper objectMapper,
                             IiifImageDownloader imageDownloader,
                             IiifProperties properties,
                             PlatformTransactionManager transactionManager,
                             Clock clock) {
        this.iiifImportJobRepository = iiifImportJobRepository;
        this.iiifImportJobItemRepository = iiifImportJobItemRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.thumbnailService = thumbnailService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.objectMapper = objectMapper;
        this.imageDownloader = imageDownloader;
        this.properties = properties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void processImportJob(String jobId, String workerId) {
        try {
            doProcessImportJob(jobId, workerId);
        } catch (JobCancelledException e) {
            handleCancellation(jobId);
        } catch (LeaseLostException e) {
            log.info("Stopped processing IIIF import job {} because its worker lease was lost", jobId);
        } catch (Exception e) {
            log.error("Failed to process IIIF import job {}", jobId, e);
            handleJobError(jobId, workerId, errorMessage(e));
        }
    }

    void doProcessImportJob(String jobId, String workerId) {
        LocalDateTime heartbeatAt = LocalDateTime.now(clock);
        Boolean claimed = transactionTemplate.execute(status -> iiifImportJobRepository.claimPendingJob(
                jobId,
                workerId,
                heartbeatAt.plusNanos(properties.getWorkerLeaseDurationMs() * 1_000_000),
                heartbeatAt
        ) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            return;
        }

        IiifImportJob job = transactionTemplate.execute(status -> {
            IiifImportJob currentJob = requireOwnedJob(jobId, workerId);
            currentJob.appendToLog(hasResults(jobId) ? "Resumed IIIF import" : "Started IIIF import");
            return iiifImportJobRepository.save(currentJob);
        });
        if (job == null) {
            throw new IllegalStateException("IIIF import job not found: " + jobId);
        }

        for (IiifJobCanvasPayload payload : readPayloads(job.getCanvasPayloadJson())) {
            IiifImportJob currentJob = refreshJob(jobId);
            requireOwnedJob(currentJob, workerId);
            if (hasResult(jobId, payload.index())) {
                continue;
            }
            processPayload(jobId, workerId, payload);
        }

        IiifImportJob completedJob = transactionTemplate.execute(status -> {
            IiifImportJob currentJob = requireOwnedJob(jobId, workerId);
            currentJob.setStatus(IiifImportJob.Status.COMPLETED);
            currentJob.setCompletedAt(LocalDateTime.now(clock));
            currentJob.clearLease();
            currentJob.appendToLog("IIIF import completed");
            return iiifImportJobRepository.save(currentJob);
        });
        if (completedJob != null) {
            releaseReservationIfNeeded(completedJob, true);
        }
    }

    private void processPayload(String jobId, String workerId, IiifJobCanvasPayload payload) {
        if ("KEEP_EXISTING".equals(payload.action())) {
            persistResult(jobId, workerId, skippedResult(payload), null);
            return;
        }

        IiifImageDownloader.DownloadedImage downloadedImage;
        DownloadBudget downloadBudget = calculateDownloadBudget(refreshJob(jobId));
        try {
            // This network operation deliberately runs outside a database transaction.
            downloadedImage = imageDownloader.download(
                    payload.imageUrl(),
                    payload.finalPageName(),
                    downloadBudget.maxBytes()
            );
        } catch (IiifImageDownloader.DownloadSizeLimitExceededException e) {
            String message = downloadBudget.quotaLimited()
                    ? "Image exceeds the remaining workspace storage allowance of "
                    + formatBytes(downloadBudget.maxBytes()) + "."
                    : "Image exceeds the configured per-image download limit of "
                    + formatBytes(downloadBudget.maxBytes()) + ".";
            log.warn("Rejected oversized IIIF canvas {} for job {}: {}", payload.canvasId(), jobId, message);
            persistResult(jobId, workerId, failedResult(payload, message), null);
            return;
        } catch (Exception e) {
            log.warn("Failed to download IIIF canvas {} for job {}: {}", payload.canvasId(), jobId, e.getMessage());
            persistResult(jobId, workerId, failedResult(payload, errorMessage(e)), null);
            return;
        }

        try (downloadedImage) {
            transactionTemplate.executeWithoutResult(status -> {
                IiifImportJob job = requireOwnedJob(jobId, workerId);
                if (hasResult(jobId, payload.index())) {
                    return;
                }
                try {
                    ensureActualBytesReserved(job, downloadedImage.sizeBytes());
                    Project project = projectRepository.findById(job.getProjectId())
                            .orElseThrow(() -> new IllegalStateException("Project not found: " + job.getProjectId()));
                    persistResult(
                            job,
                            importDownloadedCanvas(
                                    project,
                                    payload,
                                    job.getCreatedByUserId(),
                                    downloadedImage
                            ),
                            downloadedImage.sizeBytes()
                    );
                } catch (LeaseLostException | JobCancelledException e) {
                    throw e;
                } catch (Exception e) {
                    throw new CanvasPersistenceException(e);
                }
            });
        } catch (LeaseLostException | JobCancelledException e) {
            throw e;
        } catch (Exception e) {
            Throwable cause = e instanceof CanvasPersistenceException && e.getCause() != null ? e.getCause() : e;
            log.warn("Failed to import IIIF canvas {} for job {}: {}", payload.canvasId(), jobId, cause.getMessage());
            persistResult(jobId, workerId, failedResult(payload, errorMessage(cause)), null);
        }
    }

    private IiifImportDto.ItemResult importDownloadedCanvas(Project project,
                                                            IiifJobCanvasPayload payload,
                                                            String createdByUserId,
                                                            IiifImageDownloader.DownloadedImage downloadedImage)
            throws IOException {
        Page page = resolvePage(project, payload);
        replaceExistingIiifImages(page);

        String originalFileName = buildOriginalFileName(payload.finalPageName(), downloadedImage.extension());
        var storedImage = hierarchicalFileStorageService.storeFromPath(
                downloadedImage.path(),
                originalFileName,
                downloadedImage.mimeType(),
                project.getLibrary().getWorkspaceId(),
                project.getId(),
                StoredFileType.IMG,
                createdByUserId,
                false
        );

        PageImage pageImage = new PageImage(
                storedImage.originalFilename(),
                storedImage.storagePath(),
                storedImage.mimeType(),
                storedImage.sizeBytes(),
                IiifImportService.IIIF_IMAGE_VARIANT,
                page.getName(),
                page
        );
        pageImage = pageImageRepository.save(pageImage);

        String thumbnailPath = thumbnailService.generateThumbnail(storedImage.storagePath());
        if (thumbnailPath != null) {
            pageImage.setThumbnailPath(thumbnailPath);
            pageImageRepository.save(pageImage);
        }

        applyIiifProvenance(page, payload);
        pageRepository.save(page);
        return new IiifImportDto.ItemResult(
                payload.canvasId(),
                payload.canvasLabel(),
                payload.index(),
                payload.requestedPageName(),
                page.getName(),
                payload.action(),
                "IMPORTED",
                page.getId(),
                "Imported canvas into page " + page.getName()
        );
    }

    private void persistResult(
            String jobId,
            String workerId,
            IiifImportDto.ItemResult result,
            Long actualBytes
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            IiifImportJob job = requireOwnedJob(jobId, workerId);
            if (!hasResult(jobId, result.index())) {
                persistResult(job, result, actualBytes);
            }
        });
    }

    private void persistResult(IiifImportJob job, IiifImportDto.ItemResult result, Long actualBytes) {
        iiifImportJobItemRepository.save(IiifImportJobItem.fromResult(job.getId(), result, actualBytes));
        switch (result.status()) {
            case "SKIPPED" -> job.setSkippedCanvases(job.getSkippedCanvases() + 1);
            case "FAILED" -> job.setFailedCanvases(job.getFailedCanvases() + 1);
            default -> job.setProcessedCanvases(job.getProcessedCanvases() + 1);
        }
        job.appendToLog(result.message());
        iiifImportJobRepository.save(job);
    }

    private DownloadBudget calculateDownloadBudget(IiifImportJob job) {
        long configuredLimit = properties.getMaxImageDownloadBytes();
        if (!workspaceQuotaGuardService.isQuotaEnforcementEnabled()) {
            return new DownloadBudget(configuredLimit, false);
        }

        long importedBytes = iiifImportJobItemRepository.sumImportedBytes(job.getId());
        long remainingReservation = Math.max(0L, job.getReservedBytes() - importedBytes);
        long availableBytes = workspaceQuotaGuardService.getAvailableBytes(job.getWorkspaceId());
        long workspaceBudget = saturatedAdd(remainingReservation, availableBytes);
        return new DownloadBudget(
                Math.min(configuredLimit, workspaceBudget),
                workspaceBudget < configuredLimit
        );
    }

    private void ensureActualBytesReserved(IiifImportJob job, long nextImageBytes) {
        if (!workspaceQuotaGuardService.isQuotaEnforcementEnabled()) {
            return;
        }
        long importedBytes = iiifImportJobItemRepository.sumImportedBytes(job.getId());
        long requiredBytes = saturatedAdd(importedBytes, nextImageBytes);
        long additionalBytes = requiredBytes - job.getReservedBytes();
        if (additionalBytes <= 0) {
            return;
        }
        long reserved = workspaceQuotaGuardService.reserveBytesOrThrow(
                job.getWorkspaceId(),
                additionalBytes,
                "iiif-import-download"
        );
        job.setReservedBytes(saturatedAdd(job.getReservedBytes(), reserved));
    }

    private IiifImportDto.ItemResult skippedResult(IiifJobCanvasPayload payload) {
        return new IiifImportDto.ItemResult(
                payload.canvasId(),
                payload.canvasLabel(),
                payload.index(),
                payload.requestedPageName(),
                payload.finalPageName(),
                payload.action(),
                "SKIPPED",
                payload.targetPageId(),
                "Skipped because existing page was kept."
        );
    }

    private IiifImportDto.ItemResult failedResult(IiifJobCanvasPayload payload, String message) {
        return new IiifImportDto.ItemResult(
                payload.canvasId(),
                payload.canvasLabel(),
                payload.index(),
                payload.requestedPageName(),
                payload.finalPageName(),
                payload.action(),
                "FAILED",
                null,
                message
        );
    }

    private Page resolvePage(Project project, IiifJobCanvasPayload payload) {
        if ("REPLACE".equals(payload.action())) {
            return pageRepository.findByIdAndProjectId(payload.targetPageId(), project.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Target page no longer exists for replace action."
                    ));
        }

        Page page = new Page();
        page.setProject(project);
        page.setName(payload.finalPageName());
        page.setDescription(payload.pageDescription());
        page.setSortOrder(payload.sortOrder());
        page.setTags(new ArrayList<>());
        return pageRepository.save(page);
    }

    private void replaceExistingIiifImages(Page page) {
        List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(
                page.getId(),
                IiifImportService.IIIF_IMAGE_VARIANT
        );
        for (PageImage existingImage : existingImages) {
            hierarchicalFileStorageService.deleteStoredFile(existingImage.getFilePath());
            if (existingImage.getThumbnailPath() != null) {
                hierarchicalFileStorageService.deleteStoredFile(existingImage.getThumbnailPath());
            }
            pageImageRepository.delete(existingImage);
        }
    }

    private void applyIiifProvenance(Page page, IiifJobCanvasPayload payload) {
        page.setExternalSourceType(IiifImportService.EXTERNAL_SOURCE_TYPE);
        page.setExternalSourceId(payload.canvasId());
        page.setExternalSourceUrl(payload.externalSourceUrl());
        page.setExternalSourceMetadataJson(payload.externalSourceMetadataJson());
        if (!"REPLACE".equals(payload.action())
                && (page.getDescription() == null || page.getDescription().isBlank())) {
            page.setDescription(payload.pageDescription());
        }
    }

    private String buildOriginalFileName(String pageName, String extension) {
        String safeStem = pageName == null ? "canvas" : pageName
                .replaceAll("[\\\\/:*?\"<>|]+", " ")
                .replaceAll("\\p{Cntrl}+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (safeStem.isBlank()) {
            safeStem = "canvas";
        }
        return safeStem + ".iiif." + extension;
    }

    private IiifImportJob requireOwnedJob(String jobId, String workerId) {
        return requireOwnedJob(refreshJob(jobId), workerId);
    }

    private IiifImportJob requireOwnedJob(IiifImportJob job, String workerId) {
        if (job.getStatus() == IiifImportJob.Status.CANCELLED) {
            throw new JobCancelledException();
        }
        if (job.getStatus() != IiifImportJob.Status.IMPORTING
                || !Objects.equals(job.getLeaseOwner(), workerId)) {
            throw new LeaseLostException();
        }
        return job;
    }

    private IiifImportJob refreshJob(String jobId) {
        return iiifImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("IIIF import job not found: " + jobId));
    }

    private boolean hasResults(String jobId) {
        return iiifImportJobItemRepository.existsByJobId(jobId);
    }

    private boolean hasResult(String jobId, int canvasIndex) {
        return iiifImportJobItemRepository.existsByJobIdAndCanvasIndex(jobId, canvasIndex);
    }

    private List<IiifJobCanvasPayload> readPayloads(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, JOB_PAYLOAD_LIST_TYPE);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to read IIIF import payload", e);
        }
    }

    private long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.ROOT, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void handleCancellation(String jobId) {
        IiifImportJob job = transactionTemplate.execute(status -> {
            IiifImportJob cancelledJob = refreshJob(jobId);
            if (cancelledJob.getStatus() != IiifImportJob.Status.CANCELLED) {
                return null;
            }
            cancelledJob.clearLease();
            cancelledJob.appendToLog("Import cancelled");
            return iiifImportJobRepository.save(cancelledJob);
        });
        if (job != null) {
            releaseReservationIfNeeded(job, true);
        }
    }

    private void handleJobError(String jobId, String workerId, String message) {
        IiifImportJob job = transactionTemplate.execute(status -> {
            IiifImportJob failedJob = refreshJob(jobId);
            if (failedJob.getStatus() != IiifImportJob.Status.IMPORTING
                    || !Objects.equals(failedJob.getLeaseOwner(), workerId)) {
                return null;
            }
            failedJob.setStatus(IiifImportJob.Status.FAILED);
            failedJob.setErrorMessage(message);
            failedJob.setCompletedAt(LocalDateTime.now(clock));
            failedJob.clearLease();
            failedJob.appendToLog("Import failed: " + message);
            return iiifImportJobRepository.save(failedJob);
        });
        if (job != null) {
            releaseReservationIfNeeded(job, true);
        }
    }

    private void releaseReservationIfNeeded(IiifImportJob job, boolean syncUsage) {
        if (job.isQuotaReservationReleased() || job.getReservedBytes() <= 0) {
            return;
        }
        if (syncUsage) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(
                    job.getWorkspaceId(),
                    job.getReservedBytes()
            );
        } else {
            workspaceQuotaGuardService.releaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        }
        job.setQuotaReservationReleased(true);
        iiifImportJobRepository.save(job);
    }

    private String errorMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
    }

    private static final class LeaseLostException extends RuntimeException {}

    private static final class JobCancelledException extends RuntimeException {}

    private static final class CanvasPersistenceException extends RuntimeException {
        private CanvasPersistenceException(Throwable cause) {
            super(cause);
        }
    }

    private record DownloadBudget(long maxBytes, boolean quotaLimited) {}
}
