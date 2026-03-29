package de.uniwue.zpd.dachs.larex.backend.service.importer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.IiifImportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.IiifImportJob;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.IiifImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AsyncIiifImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncIiifImportProcessor.class);
    private static final TypeReference<List<IiifJobCanvasPayload>> JOB_PAYLOAD_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<IiifImportDto.ItemResult>> ITEM_RESULT_LIST_TYPE = new TypeReference<>() {};
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_HTTP_ATTEMPTS = 3;

    private final IiifImportJobRepository iiifImportJobRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final ThumbnailService thumbnailService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final ObjectMapper objectMapper;
    private final IiifRemoteRequestThrottler iiifRemoteRequestThrottler;
    private final HttpClient httpClient;
    private final TransactionTemplate transactionTemplate;

    public AsyncIiifImportProcessor(IiifImportJobRepository iiifImportJobRepository,
                                    ProjectRepository projectRepository,
                                    PageRepository pageRepository,
                                    PageImageRepository pageImageRepository,
                                    HierarchicalFileStorageService hierarchicalFileStorageService,
                                    ThumbnailService thumbnailService,
                                    WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                    ObjectMapper objectMapper,
                                    IiifRemoteRequestThrottler iiifRemoteRequestThrottler,
                                    PlatformTransactionManager transactionManager) {
        this.iiifImportJobRepository = iiifImportJobRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.thumbnailService = thumbnailService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.objectMapper = objectMapper;
        this.iiifRemoteRequestThrottler = iiifRemoteRequestThrottler;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Async("importTaskExecutor")
    public void processImportJob(String jobId) {
        try {
            doProcessImportJob(jobId);
        } catch (Exception e) {
            log.error("Failed to process IIIF import job {}", jobId, e);
            handleJobError(jobId, e.getMessage());
        }
    }

    public void doProcessImportJob(String jobId) throws IOException {
        IiifImportJob job = transactionTemplate.execute(status -> {
            IiifImportJob currentJob = iiifImportJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalStateException("IIIF import job not found: " + jobId));

            if (currentJob.getStatus() == IiifImportJob.Status.CANCELLED) {
                return currentJob;
            }

            currentJob.setStatus(IiifImportJob.Status.IMPORTING);
            currentJob.appendToLog("Started IIIF import");
            return iiifImportJobRepository.save(currentJob);
        });
        if (job == null) {
            throw new IllegalStateException("IIIF import job not found: " + jobId);
        }
        if (job.getStatus() == IiifImportJob.Status.CANCELLED) {
            releaseReservationIfNeeded(job, true);
            return;
        }

        List<IiifJobCanvasPayload> payloads = readPayloads(job.getCanvasPayloadJson());

        for (IiifJobCanvasPayload payload : payloads) {
            job = refreshJob(jobId);
            if (job.getStatus() == IiifImportJob.Status.CANCELLED) {
                transactionTemplate.executeWithoutResult(status -> {
                    IiifImportJob cancelledJob = refreshJob(jobId);
                    cancelledJob.appendToLog("Import cancelled");
                    iiifImportJobRepository.save(cancelledJob);
                });
                releaseReservationIfNeeded(job, true);
                return;
            }

            processPayload(jobId, payload);
        }

        IiifImportJob completedJob = transactionTemplate.execute(status -> {
            IiifImportJob currentJob = refreshJob(jobId);
            if (currentJob.getStatus() == IiifImportJob.Status.CANCELLED) {
                return currentJob;
            }
            currentJob.setStatus(IiifImportJob.Status.COMPLETED);
            currentJob.setCompletedAt(LocalDateTime.now());
            currentJob.appendToLog("IIIF import completed");
            return iiifImportJobRepository.save(currentJob);
        });
        if (completedJob != null) {
            releaseReservationIfNeeded(completedJob, true);
        }
    }

    private void processPayload(String jobId, IiifJobCanvasPayload payload) {
        transactionTemplate.executeWithoutResult(status -> {
            IiifImportJob job = refreshJob(jobId);
            List<IiifImportDto.ItemResult> results = readResults(job.getResultsJson());

            if (job.getStatus() == IiifImportJob.Status.CANCELLED) {
                return;
            }

            try {
                Project project = projectRepository.findById(job.getProjectId())
                        .orElseThrow(() -> new IllegalStateException("Project not found: " + job.getProjectId()));
                IiifImportDto.ItemResult result = importCanvas(project, payload, job.getCreatedByUserId());
                results.add(result);
                switch (result.status()) {
                    case "SKIPPED" -> job.setSkippedCanvases(job.getSkippedCanvases() + 1);
                    case "FAILED" -> job.setFailedCanvases(job.getFailedCanvases() + 1);
                    default -> job.setProcessedCanvases(job.getProcessedCanvases() + 1);
                }
                job.appendToLog(result.message());
            } catch (Exception e) {
                log.warn("Failed to import IIIF canvas {} for job {}: {}", payload.canvasId(), jobId, e.getMessage());
                results.add(new IiifImportDto.ItemResult(
                        payload.canvasId(),
                        payload.canvasLabel(),
                        payload.index(),
                        payload.requestedPageName(),
                        payload.finalPageName(),
                        payload.action(),
                        "FAILED",
                        null,
                        e.getMessage()
                ));
                job.setFailedCanvases(job.getFailedCanvases() + 1);
                job.appendToLog("Failed importing " + payload.canvasLabel() + ": " + e.getMessage());
            }

            job.setResultsJson(writeJson(results));
            iiifImportJobRepository.save(job);
        });
    }

    private IiifImportDto.ItemResult importCanvas(Project project, IiifJobCanvasPayload payload, String createdByUserId) throws IOException {
        if ("KEEP_EXISTING".equals(payload.action())) {
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

        Page page = resolvePage(project, payload);
        DownloadedImage downloadedImage = downloadImage(payload.imageUrl(), payload.finalPageName());
        try {
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
        } finally {
            Files.deleteIfExists(downloadedImage.path());
        }
    }

    private Page resolvePage(Project project, IiifJobCanvasPayload payload) {
        if ("REPLACE".equals(payload.action())) {
            return pageRepository.findByIdAndProjectId(payload.targetPageId(), project.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Target page no longer exists for replace action."));
        }

        Page page = new Page();
        page.setProject(project);
        page.setName(payload.finalPageName());
        page.setDescription(payload.pageDescription());
        page.setTags(new ArrayList<>());
        return pageRepository.save(page);
    }

    private void replaceExistingIiifImages(Page page) {
        List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(page.getId(), IiifImportService.IIIF_IMAGE_VARIANT);
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
        if (!"REPLACE".equals(payload.action()) && (page.getDescription() == null || page.getDescription().isBlank())) {
            page.setDescription(payload.pageDescription());
        }
    }

    private DownloadedImage downloadImage(String imageUrl, String pageName) throws IOException {
        try {
            for (int attempt = 0; attempt < MAX_HTTP_ATTEMPTS; attempt++) {
                iiifRemoteRequestThrottler.awaitRequestSlot(imageUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(imageUrl))
                        .timeout(HTTP_TIMEOUT)
                        .GET()
                        .header("Accept", "image/*, */*;q=0.8")
                        .header("User-Agent", "LAREX IIIF Import")
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String mimeType = response.headers().firstValue("content-type").orElse("application/octet-stream");
                    String extension = detectExtension(mimeType, imageUrl);
                    Path tempFile = Files.createTempFile("larex-iiif-", "." + extension);
                    try (InputStream bodyStream = response.body()) {
                        Files.copy(bodyStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    return new DownloadedImage(tempFile, mimeType, extension);
                }

                try (InputStream ignored = response.body()) {
                    if (response.statusCode() == 429 && attempt < MAX_HTTP_ATTEMPTS - 1) {
                        iiifRemoteRequestThrottler.deferAfterRateLimit(imageUrl, response.headers(), attempt);
                        continue;
                    }
                    throw new IOException(buildImageDownloadStatusMessage(response.statusCode()));
                }
            }
            throw new IOException(buildImageDownloadStatusMessage(429));
        } catch (HttpTimeoutException e) {
            throw new IOException("Timed out while downloading the IIIF image for " + pageName + ".", e);
        } catch (ConnectException e) {
            throw new IOException("Could not reach the IIIF image server for " + pageName + ".", e);
        } catch (UnknownHostException e) {
            throw new IOException("Could not resolve the IIIF image host for " + pageName + ".", e);
        } catch (IOException e) {
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                throw e;
            }
            throw new IOException("Could not download the IIIF image for " + pageName + " because of a network error.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while downloading image for " + pageName, e);
        } catch (URISyntaxException e) {
            throw new IOException("IIIF image URL is invalid: " + imageUrl, e);
        }
    }

    private String buildImageDownloadStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 401 -> "Image download failed with HTTP 401. The IIIF image requires authentication.";
            case 403 -> "Image download failed with HTTP 403. The IIIF image is not publicly accessible.";
            case 404 -> "Image download failed with HTTP 404. The IIIF image URL could not be found.";
            case 429 -> "Image download failed with HTTP 429. The IIIF server is rate limiting requests; retry later.";
            default -> "Image download failed with HTTP " + statusCode;
        };
    }

    private String detectExtension(String mimeType, String imageUrl) {
        String normalizedMime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (normalizedMime.contains("jpeg") || normalizedMime.contains("jpg")) return "jpg";
        if (normalizedMime.contains("png")) return "png";
        if (normalizedMime.contains("tiff")) return "tiff";
        if (normalizedMime.contains("webp")) return "webp";
        if (normalizedMime.contains("gif")) return "gif";
        String url = imageUrl == null ? "" : imageUrl.toLowerCase(Locale.ROOT);
        if (url.endsWith(".png")) return "png";
        if (url.endsWith(".tif") || url.endsWith(".tiff")) return "tiff";
        if (url.endsWith(".webp")) return "webp";
        if (url.endsWith(".gif")) return "gif";
        return "jpg";
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

    private IiifImportJob refreshJob(String jobId) {
        return iiifImportJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("IIIF import job not found: " + jobId));
    }

    private List<IiifJobCanvasPayload> readPayloads(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, JOB_PAYLOAD_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read IIIF import payload", e);
        }
    }

    private List<IiifImportDto.ItemResult> readResults(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, ITEM_RESULT_LIST_TYPE));
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize IIIF import results", e);
        }
    }

    private void handleJobError(String jobId, String message) {
        Optional<IiifImportJob> jobOpt = iiifImportJobRepository.findById(jobId);
        if (jobOpt.isEmpty()) {
            return;
        }
        IiifImportJob job = jobOpt.get();
        job.setStatus(IiifImportJob.Status.FAILED);
        job.setErrorMessage(message);
        job.setCompletedAt(LocalDateTime.now());
        job.appendToLog("Import failed: " + message);
        iiifImportJobRepository.save(job);
        releaseReservationIfNeeded(job, true);
    }

    private void releaseReservationIfNeeded(IiifImportJob job, boolean syncUsage) {
        if (job.isQuotaReservationReleased() || job.getReservedBytes() <= 0) {
            return;
        }
        if (syncUsage) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        } else {
            workspaceQuotaGuardService.releaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        }
        job.setQuotaReservationReleased(true);
        iiifImportJobRepository.save(job);
    }

    private record DownloadedImage(Path path, String mimeType, String extension) {}
}
