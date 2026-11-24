package de.uniwue.zpd.dachs.larex.backend.service.importer;

import de.uniwue.zpd.dachs.larex.backend.config.ImportProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.ImportJob.ImportJobStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.repository.importing.ImportJobRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.LinkedDatasetItemSync;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageFileWriteService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWriteGuard;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.OcrFormatToPageXml;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.springframework.core.io.FileSystemResource;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncImportProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncImportProcessor.class);

    private final ImportJobRepository importJobRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final NotificationService notificationService;
    private final StorageTrackingService storageTrackingService;
    private final PageFilterIndexService pageFilterIndexService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final PageXmlValidationService pageXmlValidationService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final ImportProperties properties;
    private final LinkedDatasetItemSync linkedDatasetItemSync;
    private final PageWriteGuard pageWriteGuard;
    private final PageFileWriteService pageFileWriteService;

    public AsyncImportProcessor(ImportJobRepository importJobRepository,
                                ProjectRepository projectRepository,
                                PageRepository pageRepository,
                                PageImageRepository pageImageRepository,
                                PageXmlRepository pageXmlRepository,
                                NotificationService notificationService,
                                StorageTrackingService storageTrackingService,
                                PageFilterIndexService pageFilterIndexService,
                                HierarchicalFileStorageService hierarchicalFileStorageService,
                                PageXmlCanonicalizationService pageXmlCanonicalizationService,
                                PageXmlValidationService pageXmlValidationService,
                                WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                ImportProperties properties,
                                LinkedDatasetItemSync linkedDatasetItemSync,
                                PageWriteGuard pageWriteGuard,
                                PageFileWriteService pageFileWriteService) {
        this.importJobRepository = importJobRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.notificationService = notificationService;
        this.storageTrackingService = storageTrackingService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.properties = properties;
        this.linkedDatasetItemSync = linkedDatasetItemSync;
        this.pageWriteGuard = pageWriteGuard;
        this.pageFileWriteService = pageFileWriteService;
    }

    @Async("importTaskExecutor")
    public void processImportJob(String jobId, boolean canReviewPages) {
        log.info("Starting async processing for import job: {}", jobId);

        try {
            doProcessImportJob(jobId, canReviewPages);
        } catch (Exception e) {
            log.error("Failed to process import job: {}", jobId, e);
            handleJobError(jobId, e.getMessage());
        }
    }

    public void doProcessImportJob(String jobId, boolean canReviewPages) throws IOException {
        ImportJob job = importJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Import job not found: " + jobId));

        if (job.getStatus() == ImportJobStatus.CANCELLED) {
            log.info("Import job {} was cancelled, skipping processing", jobId);
            releaseJobReservationIfNeeded(job, true);
            return;
        }

        final String projectId = job.getProjectId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

        Path sourcePath = Paths.get(job.getSourcePath());
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IOException("Source path is not a valid directory: " + sourcePath);
        }

        // Phase 1: Scanning
        job.setStatus(ImportJobStatus.SCANNING);
        importJobRepository.save(job);
        job.appendToLog("Started scanning: " + sourcePath);

        List<Path> filesToImport = new ArrayList<>();
        long totalBytes = 0;

        try (Stream<Path> stream = Files.walk(sourcePath, properties.getMaxScanDepth())) {
            List<Path> allFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> isImageFile(p) || isXmlFile(p))
                    .limit(properties.getMaxFilesPerJob())
                    .toList();

            for (Path filePath : allFiles) {
                filesToImport.add(filePath);
                totalBytes += Files.size(filePath);
            }
        }

        job.setTotalFiles(filesToImport.size());
        job.setTotalBytes(totalBytes);
        job.appendToLog("Found " + filesToImport.size() + " files to import");
        importJobRepository.save(job);

        if (filesToImport.isEmpty()) {
            job.setStatus(ImportJobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.appendToLog("No files to import");
            importJobRepository.save(job);
            return;
        }

        // Check for cancellation
        job = refreshJobStatus(job);
        if (job.getStatus() == ImportJobStatus.CANCELLED) {
            releaseJobReservationIfNeeded(job, true);
            return;
        }

        // Phase 2: Validating
        job.setStatus(ImportJobStatus.VALIDATING);
        importJobRepository.save(job);

        // Group files by base name
        Map<String, List<Path>> filesByBaseName = new HashMap<>();
        for (Path filePath : filesToImport) {
            String fileName = filePath.getFileName().toString();
            ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(fileName);
            filesByBaseName.computeIfAbsent(nameInfo.baseName(), k -> new ArrayList<>()).add(filePath);
        }
        Map<String, Page> pagesByName = pageRepository.findByProjectIdAndNameIn(project.getId(), filesByBaseName.keySet())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Page::getName, p -> p));

        // Check for cancellation
        job = refreshJobStatus(job);
        if (job.getStatus() == ImportJobStatus.CANCELLED) {
            releaseJobReservationIfNeeded(job, true);
            return;
        }

        // Phase 3: Importing
        job.setStatus(ImportJobStatus.IMPORTING);
        importJobRepository.save(job);

        int processedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<String> lockReasons = new ArrayList<>();

        for (Map.Entry<String, List<Path>> entry : filesByBaseName.entrySet()) {
            // Check for cancellation periodically
            if (processedCount % 100 == 0) {
                job = refreshJobStatus(job);
                if (job.getStatus() == ImportJobStatus.CANCELLED) {
                    job.appendToLog("Import cancelled at " + processedCount + " files");
                    releaseJobReservationIfNeeded(job, true);
                    importJobRepository.save(job);
                    return;
                }
            }

            String baseName = entry.getKey();
            List<Path> groupFiles = entry.getValue();

            try {
                GroupResult result = processFileGroup(project, job.getWorkspaceId(), baseName, groupFiles, pagesByName,
                        job.isOverwriteExisting(), job.getCreatedByUserId(), canReviewPages);
                processedCount += result.processed();
                skippedCount += result.skipped();
                if (result.lockReason() != null) {
                    lockReasons.add(result.lockReason());
                    job.appendToLog("Skipped files of " + baseName + ": " + result.lockReason());
                }

                job.setProcessedFiles(processedCount);
                job.setSkippedFiles(skippedCount);
                importJobRepository.save(job);

            } catch (Exception e) {
                log.error("Failed to import file group: {}", baseName, e);
                failedCount += groupFiles.size();
                job.setFailedFiles(failedCount);
                job.appendToLog("Error importing " + baseName + ": " + e.getMessage());
                importJobRepository.save(job);
            }
        }

        // Complete job
        job.setStatus(ImportJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.appendToLog("Import completed: " + processedCount + " processed, " +
                skippedCount + " skipped, " + failedCount + " failed");
        if (!lockReasons.isEmpty()) {
            job.setErrorMessage("Skipped files of locked pages: " + PageWriteGuard.summarize(lockReasons));
        }
        importJobRepository.save(job);

        try {
            releaseJobReservationIfNeeded(job, true);
            importJobRepository.save(job);
        } catch (Exception e) {
            log.warn("Failed to settle storage quota reservation for workspace: {}", job.getWorkspaceId(), e);
        }

        // Send notification
        notificationService.createImportCompletedNotification(
                job.getCreatedByUserId(),
                project.getName(),
                project.getId(),
                job.getTotalFiles(),
                failedCount
        );

        log.info("Completed import job {}: {} processed, {} skipped, {} failed",
                jobId, processedCount, skippedCount, failedCount);
    }

    private record GroupResult(int processed, int skipped, String lockReason) {
    }

    private GroupResult processFileGroup(Project project, String workspaceId, String baseName, List<Path> files,
                                         Map<String, Page> pagesByName, boolean overwriteExisting,
                                         String createdByUserId, boolean canReviewPages) throws IOException {
        int processed = 0;
        int skipped = 0;
        String lockReason = null;

        if (!pagesByName.containsKey(baseName)) {
            Optional<String> projectLockReason = pageWriteGuard.projectLockReason(project.getId());
            if (projectLockReason.isPresent()) {
                return new GroupResult(0, files.size(), projectLockReason.get());
            }
        }

        // Find or create page
        Page page = pagesByName.computeIfAbsent(baseName, name -> createPage(project, name));

        List<Path> ordered = files.stream().sorted(Comparator.comparing(this::isXmlFile)).toList();
        for (Path filePath : ordered) {
            String fileName = filePath.getFileName().toString();

            if (isImageFile(filePath)) {
                ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(fileName);

                // Check for existing image with same variant
                List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(
                        page.getId(), nameInfo.variant());

                if (!existingImages.isEmpty() && !overwriteExisting) {
                    skipped++;
                    continue;
                }

                Optional<String> imageLockReason = pageFileWriteService.writeUnlessLocked(page.getId(), canReviewPages,
                        changes -> importImage(project, workspaceId, page, filePath, nameInfo, createdByUserId, changes));
                if (imageLockReason.isPresent()) {
                    lockReason = imageLockReason.get();
                    skipped++;
                    continue;
                }
                processed++;

            } else if (isXmlFile(filePath)) {
                Optional<PageXml> existingXml = pageXmlRepository.findByPage_Id(page.getId());
                if (existingXml.isPresent() && !overwriteExisting) {
                    skipped++;
                    continue;
                }
                Optional<byte[]> converted = pageXmlValidationService.toImportablePageXml(new FileSystemResource(filePath),
                        pageXmlCanonicalizationService.storedPageImage(page.getId()));
                Path xmlSource = converted.isPresent() ? writeConvertedCopy(converted.get()) : filePath;
                Optional<String> xmlLockReason;
                try {
                    xmlLockReason = pageFileWriteService.writeUnlessLocked(page.getId(), canReviewPages,
                            changes -> importXml(project, workspaceId, page, xmlSource, fileName, converted.isPresent(), createdByUserId, changes));
                } finally {
                    if (converted.isPresent()) {
                        Files.deleteIfExists(xmlSource);
                    }
                }
                if (xmlLockReason.isPresent()) {
                    lockReason = xmlLockReason.get();
                    skipped++;
                    continue;
                }

                // Index the page content for filtering
                try {
                    pageFilterIndexService.indexPageFromXml(page);
                } catch (Exception e) {
                    log.warn("Failed to index page {} after XML import: {}", page.getId(), e.getMessage());
                }

                processed++;
            }
        }

        return new GroupResult(processed, skipped, lockReason);
    }

    private void importImage(Project project, String workspaceId, Page page, Path filePath, ImageFileUtils.ImageNameInfo nameInfo,
                             String createdByUserId, PageFileWriteService.FileChanges changes) throws IOException {
        String fileName = filePath.getFileName().toString();
        List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(page.getId(), nameInfo.variant());

        var storedImage = hierarchicalFileStorageService.storeFromPath(
                filePath,
                fileName,
                detectMimeType(fileName),
                workspaceId,
                project.getId(),
                StoredFileType.IMG,
                createdByUserId,
                false
        );
        changes.stored(storedImage.storagePath());

        if (!existingImages.isEmpty()) {
            PageImage existing = existingImages.get(0);
            changes.replaced(existing.getFilePath());
            changes.replaced(existing.getThumbnailPath());
            pageImageRepository.delete(existing);
        }

        PageImage pageImage = new PageImage(
                storedImage.originalFilename(),
                storedImage.storagePath(),
                storedImage.mimeType(),
                storedImage.sizeBytes(),
                nameInfo.variant(),
                nameInfo.baseName(),
                page
        );
        pageImage.setImageType(storedImage.imageType());
        pageImageRepository.save(pageImage);
    }

    private void importXml(Project project, String workspaceId, Page page, Path xmlSource, String fileName, boolean moveSource,
                           String createdByUserId, PageFileWriteService.FileChanges changes) throws IOException {
        pageXmlRepository.findByPage_Id(page.getId()).ifPresent(headXml -> {
            changes.replaced(headXml.getFilePath());
            pageXmlRepository.delete(headXml);
            pageXmlRepository.flush();
        });

        var storedXml = hierarchicalFileStorageService.storeFromPath(
                xmlSource,
                OcrFormatToPageXml.pageXmlFileName(fileName),
                "application/xml",
                workspaceId,
                project.getId(),
                StoredFileType.XML,
                createdByUserId,
                moveSource
        );
        changes.stored(storedXml.storagePath());

        String xmlBaseName = (storedXml.originalFilename() != null && storedXml.originalFilename().contains("."))
                ? storedXml.originalFilename().substring(0, storedXml.originalFilename().lastIndexOf('.'))
                : storedXml.originalFilename();

        PageXml pageXml = new PageXml(
                storedXml.originalFilename(),
                storedXml.storagePath(),
                storedXml.mimeType(),
                storedXml.sizeBytes(),
                "original",
                xmlBaseName,
                XmlSchema.PAGE_XML, null, page
        );
        pageXml = pageXmlRepository.save(pageXml);
        pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, createdByUserId, "server import");
        linkedDatasetItemSync.relink(pageXml);
    }

    private Path writeConvertedCopy(byte[] pageXml) throws IOException {
        Path converted = Files.createTempFile("larex-converted-", ".xml");
        Files.write(converted, pageXml);
        return converted;
    }

    private Page createPage(Project project, String baseName) {
        Page page = new Page();
        page.setProject(project);
        page.setName(baseName);
        page.setDescription("Auto-created from server import");
        page.setTags(new ArrayList<>());
        return pageRepository.save(page);
    }

    private ImportJob refreshJobStatus(ImportJob job) {
        return importJobRepository.findById(job.getId()).orElse(job);
    }

    private boolean isImageFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
               fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".tiff") ||
               fileName.endsWith(".webp");
    }

    private boolean isXmlFile(Path path) {
        return OcrFormatToPageXml.hasSupportedExtension(path.getFileName().toString());
    }

    private String detectMimeType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".bmp")) return "image/bmp";
        if (lowerName.endsWith(".tiff")) return "image/tiff";
        if (lowerName.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private void handleJobError(String jobId, String errorMessage) {
        try {
            ImportJob job = importJobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(ImportJobStatus.FAILED);
                job.setErrorMessage(errorMessage);
                job.setCompletedAt(LocalDateTime.now());
                job.appendToLog("Import failed: " + errorMessage);
                releaseJobReservationIfNeeded(job, true);
                importJobRepository.save(job);

                Project project = projectRepository.findById(job.getProjectId()).orElse(null);
                if (project != null) {
                    notificationService.createImportFailedNotification(
                            job.getCreatedByUserId(),
                            project.getName(),
                            project.getId(),
                            errorMessage
                    );
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle job error for job: {}", jobId, e);
        }
    }

    private void releaseJobReservationIfNeeded(ImportJob job, boolean syncUsage) {
        if (job == null || job.isQuotaReservationReleased() || job.getReservedBytes() <= 0) {
            return;
        }

        if (syncUsage) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        } else {
            workspaceQuotaGuardService.releaseReservation(job.getWorkspaceId(), job.getReservedBytes());
        }

        job.setQuotaReservationReleased(true);
        job.setReservedBytes(0L);
    }
}
