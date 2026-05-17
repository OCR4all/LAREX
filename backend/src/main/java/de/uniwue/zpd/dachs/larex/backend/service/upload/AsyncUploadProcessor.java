package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.*;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadPageIndexingRequestedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;


import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class AsyncUploadProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncUploadProcessor.class);

    private final UploadSessionRepository sessionRepository;
    private final UploadSessionFileRepository fileRepository;
    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final NotificationService notificationService;
    private final StorageTrackingService storageTrackingService;
    private final PageFilterIndexService pageFilterIndexService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final UploadProperties uploadProperties;

    public AsyncUploadProcessor(UploadSessionRepository sessionRepository,
                                UploadSessionFileRepository fileRepository,
                                ProjectRepository projectRepository,
                                PageRepository pageRepository,
                                PageImageRepository pageImageRepository,
                                PageXmlRepository pageXmlRepository,
                                NotificationService notificationService,
                                StorageTrackingService storageTrackingService,
                                PageFilterIndexService pageFilterIndexService,
                                HierarchicalFileStorageService hierarchicalFileStorageService,
                                PageXmlCanonicalizationService pageXmlCanonicalizationService,
                                ApplicationEventPublisher applicationEventPublisher,
                                UploadSessionEventBroadcaster uploadSessionEventBroadcaster,
                                WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                UploadProperties uploadProperties) {
        this.sessionRepository = sessionRepository;
        this.fileRepository = fileRepository;
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.notificationService = notificationService;
        this.storageTrackingService = storageTrackingService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.uploadProperties = uploadProperties;
    }

    public void processUploadSession(String sessionId) {
        processUploadSessionWork(sessionId);
    }

    public void processUploadSessionWork(String sessionId) {
        log.info("Starting async processing for upload session: {}", sessionId);

        try {
            doProcessUploadSession(sessionId);
        } catch (Exception e) {
            if (isSessionCancelled(sessionId)) {
                log.info("Upload session {} cancelled while processing. Stopping worker loop.", sessionId);
                return;
            }
            log.error("Failed to process upload session: {}", sessionId, e);
            handleSessionError(sessionId, e.getMessage());
        }
    }

    public void doProcessUploadSession(String sessionId) {
        UploadSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (isSessionCancelled(sessionId)) {
            return;
        }

        if (session.getStatus() != UploadSessionStatus.UPLOADING && session.getStatus() != UploadSessionStatus.PROCESSING) {
            log.debug("Session {} is not in upload-processing state, current state: {}", sessionId, session.getStatus());
            if (session.getStatus() == UploadSessionStatus.CANCELLED || session.getStatus() == UploadSessionStatus.FAILED) {
                releaseSessionReservationIfNeeded(session, true);
            }
            return;
        }

        Project project = projectRepository.findById(session.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + session.getProjectId()));

        List<UploadSessionFile> filesToProcess = fileRepository.findBySessionIdAndStatus(sessionId, UploadFileStatus.UPLOADED);

        List<UploadSessionFile> pdfFiles = new ArrayList<>();

        // Group files by base name
        Map<String, List<UploadSessionFile>> filesByBaseName = new HashMap<>();
        for (UploadSessionFile file : filesToProcess) {
            if (isPdfFile(file)) {
                pdfFiles.add(file);
            } else {
                filesByBaseName.computeIfAbsent(file.getBaseName(), k -> new ArrayList<>()).add(file);
            }
        }
        Map<String, Page> pagesByName = pageRepository.findByProjectIdAndNameIn(project.getId(), filesByBaseName.keySet())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Page::getName, page -> page));
        Set<String> existingPageNames = new HashSet<>(pageRepository.findPageNamesByProjectId(project.getId()));

        int processedCount = 0;
        int failedCount = 0;
        Set<String> pagesNeedingIndex = new LinkedHashSet<>();

        for (UploadSessionFile pdfFile : pdfFiles) {
            if (stopProcessingIfCancelled(sessionId, session)) {
                return;
            }
            try {
                pdfFile.setStatus(UploadFileStatus.PROCESSING);
                fileRepository.save(pdfFile);
                emitFileState(sessionId, pdfFile);

                boolean processed = processPdfFile(sessionId, project, session.getWorkspaceId(), session.getUserId(), pdfFile, existingPageNames);
                if (!processed || stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }

                fileRepository.save(pdfFile);

                if (pdfFile.getStatus() == UploadFileStatus.COMPLETED) {
                    processedCount++;
                    session.incrementProcessedFiles();
                    session.addProcessedBytes(pdfFile.getFileSize());
                } else if (pdfFile.getStatus() == UploadFileStatus.FAILED) {
                    failedCount++;
                    session.incrementFailedFiles();
                }

                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                if (!persistSessionProgressIfNotCancelled(sessionId, session)) {
                    return;
                }
                emitFileState(sessionId, pdfFile);
                emitSessionState(sessionId, "file-processed");
            } catch (Exception e) {
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                log.error("Failed to process PDF file: {}", pdfFile.getOriginalFileName(), e);
                pdfFile.setStatus(UploadFileStatus.FAILED);
                pdfFile.setErrorMessage(e.getMessage());
                fileRepository.save(pdfFile);
                failedCount++;
                session.incrementFailedFiles();
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                if (!persistSessionProgressIfNotCancelled(sessionId, session)) {
                    return;
                }
                emitFileState(sessionId, pdfFile);
                emitSessionState(sessionId, "file-failed");
            }
        }

        for (Map.Entry<String, List<UploadSessionFile>> entry : filesByBaseName.entrySet()) {
            String baseName = entry.getKey();
            List<UploadSessionFile> groupFiles = entry.getValue();

            try {
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                boolean processed = processFileGroup(sessionId, project, session.getWorkspaceId(), baseName, groupFiles, session.getUserId(), pagesByName, existingPageNames, pagesNeedingIndex);
                if (!processed || stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }

                for (UploadSessionFile file : groupFiles) {
                    if (file.getStatus() == UploadFileStatus.COMPLETED) {
                        processedCount++;
                        session.incrementProcessedFiles();
                        session.addProcessedBytes(file.getFileSize());
                    } else if (file.getStatus() == UploadFileStatus.FAILED) {
                        failedCount++;
                        session.incrementFailedFiles();
                    }
                }

                // Update session progress
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                if (!persistSessionProgressIfNotCancelled(sessionId, session)) {
                    return;
                }
                emitSessionState(sessionId, "group-processed");

            } catch (Exception e) {
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                log.error("Failed to process file group for basename: {}", baseName, e);
                for (UploadSessionFile file : groupFiles) {
                    file.setStatus(UploadFileStatus.FAILED);
                    file.setErrorMessage(e.getMessage());
                    fileRepository.save(file);
                    emitFileState(sessionId, file);
                    failedCount++;
                    session.incrementFailedFiles();
                }
                if (stopProcessingIfCancelled(sessionId, session)) {
                    return;
                }
                if (!persistSessionProgressIfNotCancelled(sessionId, session)) {
                    return;
                }
                emitSessionState(sessionId, "group-failed");
            }
        }

        if (!isSessionCancelled(sessionId)) {
            finalizeSessionIfReady(sessionId);
        }

        log.info("Processed upload session work cycle {}: {} processed, {} failed",
                sessionId, processedCount, failedCount);
    }

    private boolean processFileGroup(String sessionId,
                                     Project project,
                                     String workspaceId,
                                     String baseName,
                                     List<UploadSessionFile> files,
                                     String createdByUserId,
                                     Map<String, Page> pagesByName,
                                     Set<String> existingPageNames,
                                     Set<String> pagesNeedingIndex) throws IOException {
        if (isSessionCancelled(sessionId)) {
            return false;
        }

        // Find or create page
        Page page = pagesByName.get(baseName);
        if (page == null) {
            page = createPage(project, baseName);
            pagesByName.put(baseName, page);
            existingPageNames.add(baseName);
        }

        for (UploadSessionFile sessionFile : files) {
            if (isSessionCancelled(sessionId)) {
                return false;
            }

            sessionFile.setStatus(UploadFileStatus.PROCESSING);
            fileRepository.save(sessionFile);
            emitFileState(sessionId, sessionFile);

            try {
                if (isImageFile(sessionFile)) {
                    processImageFile(page, sessionFile, workspaceId, project.getId(), createdByUserId);
                    if (sessionFile.getStatus() == UploadFileStatus.COMPLETED) {
                        uploadSessionEventBroadcaster.broadcastPageCreatedOrUpdated(sessionId, project.getId(), page.getId(), page.getName(), "image");
                    }
                } else if (isXmlFile(sessionFile)) {
                    boolean queuedForIndex = processXmlFile(page, sessionFile, workspaceId, project.getId(), createdByUserId);
                    if (sessionFile.getStatus() == UploadFileStatus.COMPLETED) {
                        uploadSessionEventBroadcaster.broadcastPageCreatedOrUpdated(sessionId, project.getId(), page.getId(), page.getName(), "xml");
                    }
                    if (queuedForIndex) {
                        queuePageIndexing(sessionId, project.getId(), page.getId(), pagesNeedingIndex);
                    }
                } else {
                    sessionFile.setStatus(UploadFileStatus.SKIPPED);
                    sessionFile.setErrorMessage("Unsupported file type");
                }

                if (isSessionCancelled(sessionId)) {
                    return false;
                }
                fileRepository.save(sessionFile);
                emitFileState(sessionId, sessionFile);

            } catch (Exception e) {
                if (isSessionCancelled(sessionId)) {
                    return false;
                }
                sessionFile.setStatus(UploadFileStatus.FAILED);
                sessionFile.setErrorMessage(e.getMessage());
                fileRepository.save(sessionFile);
                emitFileState(sessionId, sessionFile);
                throw e;
            }
        }

        return true;
    }

    private void processImageFile(Page page,
                                  UploadSessionFile sessionFile,
                                  String workspaceId,
                                  String projectId,
                                  String createdByUserId) throws IOException {
        Path tempFilePath = Paths.get(sessionFile.getTempFilePath());
        if (!Files.exists(tempFilePath)) {
            throw new IOException("Temp file not found: " + tempFilePath);
        }

        // Check for existing image with same variant
        List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(page.getId(), sessionFile.getVariant());

        String conflictResolution = sessionFile.getConflictResolution();
        if (!existingImages.isEmpty()) {
            if ("skip".equals(conflictResolution) || "keep_existing".equals(conflictResolution)) {
                sessionFile.setStatus(UploadFileStatus.SKIPPED);
                sessionFile.setConflictType("IMAGE_VARIANT_EXISTS");
                Files.deleteIfExists(tempFilePath);
                return;
            } else if ("replace".equals(conflictResolution) || "replace_with_new".equals(conflictResolution)) {
                // Delete existing image
                PageImage existingImage = existingImages.get(0);
                hierarchicalFileStorageService.deleteStoredFile(existingImage.getFilePath());
                if (existingImage.getThumbnailPath() != null) {
                    hierarchicalFileStorageService.deleteStoredFile(existingImage.getThumbnailPath());
                }
                pageImageRepository.delete(existingImage);
            } else {
                // Default: create conflict status
                sessionFile.setStatus(UploadFileStatus.CONFLICT);
                sessionFile.setConflictType("IMAGE_VARIANT_EXISTS");
                return;
            }
        }

        var storedImage = hierarchicalFileStorageService.storeFromPath(
                tempFilePath,
                sessionFile.getOriginalFileName(),
                sessionFile.getMimeType(),
                workspaceId,
                projectId,
                StoredFileType.IMG,
                createdByUserId,
                true
        );

        // Create PageImage entity
        PageImage pageImage = new PageImage(
                storedImage.originalFilename(),
                storedImage.storagePath(),
                storedImage.mimeType(),
                storedImage.sizeBytes(),
                sessionFile.getVariant(),
                sessionFile.getBaseName(),
                page
        );

        pageImage = pageImageRepository.save(pageImage);

        sessionFile.setStatus(UploadFileStatus.COMPLETED);
        sessionFile.setCreatedPageId(page.getId());
        sessionFile.setCreatedPageImageId(pageImage.getId());
    }

    private boolean processXmlFile(Page page,
                                   UploadSessionFile sessionFile,
                                   String workspaceId,
                                   String projectId,
                                   String createdByUserId) throws IOException {
        Path tempFilePath = Paths.get(sessionFile.getTempFilePath());
        if (!Files.exists(tempFilePath)) {
            throw new IOException("Temp file not found: " + tempFilePath);
        }

        List<PageXml> existingXmlFiles = pageXmlRepository.findByPage_Id(page.getId());
        String conflictResolution = sessionFile.getConflictResolution();
        if (!existingXmlFiles.isEmpty()) {
            if ("skip".equals(conflictResolution) || "keep_existing".equals(conflictResolution)) {
                sessionFile.setStatus(UploadFileStatus.SKIPPED);
                sessionFile.setConflictType("XML_FILE_EXISTS");
                Files.deleteIfExists(tempFilePath);
                return false;
            } else if ("replace".equals(conflictResolution) || "replace_with_new".equals(conflictResolution)) {
                // Delete existing XML files
                for (PageXml existingXml : existingXmlFiles) {
                    hierarchicalFileStorageService.deleteStoredFile(existingXml.getFilePath());
                    pageXmlRepository.delete(existingXml);
                }
            } else {
                // Default: create conflict status
                sessionFile.setStatus(UploadFileStatus.CONFLICT);
                sessionFile.setConflictType("XML_FILE_EXISTS");
                return false;
            }
        }

        var storedXml = hierarchicalFileStorageService.storeFromPath(
                tempFilePath,
                sessionFile.getOriginalFileName(),
                sessionFile.getMimeType(),
                workspaceId,
                projectId,
                StoredFileType.XML,
                createdByUserId,
                true
        );

        // Create PageXml entity
        String originalFileName = storedXml.originalFilename();
        String baseName = (originalFileName != null && originalFileName.contains("."))
                ? originalFileName.substring(0, originalFileName.lastIndexOf('.'))
                : originalFileName;

        PageXml pageXml = new PageXml(
                originalFileName,
                storedXml.storagePath(),
                storedXml.mimeType(),
                storedXml.sizeBytes(),
                "original",
                baseName,
                XmlSchema.PAGE_XML, null, page
        );
        pageXml = pageXmlRepository.save(pageXml);
        pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, createdByUserId, "async chunked upload");

        // Clear stale filter index rows; background indexing will rebuild after commit.
        pageFilterIndexService.clearPageIndex(page.getId());

        sessionFile.setStatus(UploadFileStatus.COMPLETED);
        sessionFile.setCreatedPageId(page.getId());
        return true;
    }

    private boolean processPdfFile(String sessionId,
                                   Project project,
                                   String workspaceId,
                                   String createdByUserId,
                                   UploadSessionFile sessionFile,
                                   Set<String> existingPageNames) throws IOException {
        if (isSessionCancelled(sessionId)) {
            return false;
        }

        Path tempFilePath = Paths.get(sessionFile.getTempFilePath());
        if (!Files.exists(tempFilePath)) {
            throw new IOException("Temp file not found: " + tempFilePath);
        }

        String prefix = sessionFile.getBaseName();
        if (prefix == null || prefix.isBlank()) {
            String originalFileName = sessionFile.getOriginalFileName();
            int dotIndex = originalFileName != null ? originalFileName.indexOf('.') : -1;
            prefix = (dotIndex >= 0 && originalFileName != null) ? originalFileName.substring(0, dotIndex) : (originalFileName != null ? originalFileName : "pdf");
        }
        prefix = prefix.trim();

        try (PDDocument document = Loader.loadPDF(tempFilePath.toFile())) {
            int pageCount = document.getNumberOfPages();
            int padWidth = Math.max(3, String.valueOf(pageCount).length());
            PDFRenderer renderer = new PDFRenderer(document);

            String firstCreatedPageId = null;
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                if (isSessionCancelled(sessionId)) {
                    return false;
                }
                String pageNumber = String.format("%0" + padWidth + "d", pageIndex + 1);
                String basePageName = prefix + "_" + pageNumber;
                String pageName = resolveUniquePageName(existingPageNames, basePageName);
                Page page = createPage(project, pageName);
                existingPageNames.add(pageName);

                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 250, ImageType.RGB);
                String pageImageFileName = pageName + ".png";
                var storedImage = hierarchicalFileStorageService.storeBufferedImage(
                        image,
                        "png",
                        pageImageFileName,
                        workspaceId,
                        project.getId(),
                        createdByUserId
                );

                PageImage pageImage = new PageImage(
                        storedImage.originalFilename(),
                        storedImage.storagePath(),
                        storedImage.mimeType(),
                        storedImage.sizeBytes(),
                        "png",
                        pageName,
                        page
                );

                pageImage = pageImageRepository.save(pageImage);
                uploadSessionEventBroadcaster.broadcastPageCreatedOrUpdated(sessionId, project.getId(), page.getId(), page.getName(), "pdf");

                if (firstCreatedPageId == null) {
                    firstCreatedPageId = page.getId();
                    sessionFile.setCreatedPageId(firstCreatedPageId);
                    sessionFile.setCreatedPageImageId(pageImage.getId());
                }
            }

            sessionFile.setStatus(UploadFileStatus.COMPLETED);
            Files.deleteIfExists(tempFilePath);
            return true;
        }
    }

    private void finalizeSessionIfReady(String sessionId) {
        if (isSessionCancelled(sessionId)) {
            return;
        }

        UploadSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != UploadSessionStatus.PROCESSING) {
            return;
        }

        List<UploadSessionFile> pendingOrUploading = fileRepository.findBySessionIdAndStatusIn(
                sessionId, List.of(UploadFileStatus.PENDING, UploadFileStatus.UPLOADING)
        );
        if (!pendingOrUploading.isEmpty()) {
            return;
        }

        List<UploadSessionFile> processingBacklog = fileRepository.findBySessionIdAndStatusIn(
                sessionId, List.of(UploadFileStatus.UPLOADED, UploadFileStatus.PROCESSING)
        );
        if (!processingBacklog.isEmpty()) {
            return;
        }

        Project project = projectRepository.findById(session.getProjectId()).orElse(null);
        if (project == null) {
            throw new RuntimeException("Project not found: " + session.getProjectId());
        }

        List<UploadSessionFile> conflictFiles = fileRepository.findBySessionIdAndStatus(sessionId, UploadFileStatus.CONFLICT);
        int conflictCount = conflictFiles.size();

        if (isSessionCancelled(sessionId)) {
            return;
        }
        LocalDateTime completedAt = LocalDateTime.now();
        int completedRows = sessionRepository.updateStatusAndCompletedAtIfStatusNot(
                sessionId,
                UploadSessionStatus.COMPLETED,
                completedAt,
                completedAt,
                UploadSessionStatus.CANCELLED
        );
        if (completedRows == 0) {
            emitSessionState(sessionId, "cancelled");
            return;
        }
        session.setStatus(UploadSessionStatus.COMPLETED);
        session.setCompletedAt(completedAt);
        emitSessionState(sessionId, "completed");

        cleanupTempFilesExceptConflicts(sessionId, conflictFiles);

        try {
            releaseSessionReservationIfNeeded(session, true);
        } catch (Exception e) {
            log.warn("Failed to settle quota reservation for workspace: {}", session.getWorkspaceId(), e);
        }

        if (conflictCount > 0) {
            notificationService.createUploadConflictsNotification(
                    session.getUserId(),
                    project.getName(),
                    project.getId(),
                    session.getTotalFiles(),
                    session.getProcessedFiles(),
                    conflictCount
            );
        } else {
            notificationService.createUploadCompletedNotification(
                    session.getUserId(),
                    project.getName(),
                    project.getId(),
                    session.getTotalFiles(),
                    session.getFailedFiles()
            );
        }
    }

    private String resolveUniquePageName(Set<String> existingPageNames, String baseName) {
        if (!existingPageNames.contains(baseName)) {
            return baseName;
        }

        int suffix = 2;
        while (true) {
            String candidate = baseName + "_" + suffix;
            if (!existingPageNames.contains(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }

    private Page createPage(Project project, String baseName) {
        Page page = new Page();
        page.setProject(project);
        page.setName(baseName);
        page.setTags(new ArrayList<>());
        return pageRepository.save(page);
    }

    private boolean isImageFile(UploadSessionFile file) {
        String mimeType = file.getMimeType();
        if (mimeType != null && mimeType.startsWith("image/")) {
            return true;
        }
        String fileName = file.getOriginalFileName().toLowerCase();
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") ||
               fileName.endsWith(".jpeg") || fileName.endsWith(".gif") ||
               fileName.endsWith(".bmp") || fileName.endsWith(".tiff") ||
               fileName.endsWith(".webp");
    }

    private boolean isXmlFile(UploadSessionFile file) {
        String fileName = file.getOriginalFileName().toLowerCase();
        return fileName.endsWith(".xml");
    }

    private boolean isPdfFile(UploadSessionFile file) {
        String mimeType = file.getMimeType();
        if (mimeType != null && mimeType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        String fileName = file.getOriginalFileName() != null ? file.getOriginalFileName().toLowerCase() : "";
        return fileName.endsWith(".pdf");
    }

    private void queuePageIndexing(String sessionId, String projectId, String pageId, Set<String> pagesNeedingIndex) {
        if (!pagesNeedingIndex.add(pageId)) {
            return;
        }
        try {
            applicationEventPublisher.publishEvent(new UploadPageIndexingRequestedEvent(sessionId, projectId, Set.of(pageId)));
        } catch (Exception e) {
            pagesNeedingIndex.remove(pageId);
            // Indexing is best-effort and should not fail upload processing/cancellation.
            log.warn("Failed to queue background page indexing for upload session {} (project {}, page {}): {}",
                    sessionId, projectId, pageId, e.getMessage(), e);
        }
    }

    private void emitFileState(String sessionId, UploadSessionFile file) {
        try {
            uploadSessionEventBroadcaster.broadcastFileState(sessionId, file);
        } catch (Exception e) {
            log.debug("Failed to broadcast upload file state for session {} file {}: {}", sessionId, file != null ? file.getId() : "null", e.getMessage());
        }
    }

    private void emitSessionState(String sessionId, String message) {
        try {
            uploadSessionEventBroadcaster.broadcastSessionState(sessionId, message);
        } catch (Exception e) {
            log.debug("Failed to broadcast upload session state for session {}: {}", sessionId, e.getMessage());
        }
    }

    private void handleSessionError(String sessionId, String errorMessage) {
        try {
            UploadSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session != null) {
                if (isSessionCancelled(sessionId) || session.getStatus() == UploadSessionStatus.CANCELLED) {
                    emitSessionState(sessionId, "cancelled");
                    return;
                }

                LocalDateTime completedAt = LocalDateTime.now();
                int failedRows = sessionRepository.updateStatusErrorAndCompletedAtIfStatusNot(
                        sessionId,
                        UploadSessionStatus.FAILED,
                        errorMessage,
                        completedAt,
                        completedAt,
                        UploadSessionStatus.CANCELLED
                );
                if (failedRows == 0 || isSessionCancelled(sessionId)) {
                    emitSessionState(sessionId, "cancelled");
                    return;
                }

                session.setStatus(UploadSessionStatus.FAILED);
                session.setErrorMessage(errorMessage);
                session.setCompletedAt(completedAt);
                releaseSessionReservationIfNeeded(session, true);
                sessionRepository.save(session);
                emitSessionState(sessionId, "failed");

                Project project = projectRepository.findById(session.getProjectId()).orElse(null);
                if (project != null) {
                    notificationService.createUploadFailedNotification(
                            session.getUserId(),
                            project.getName(),
                            project.getId(),
                            errorMessage
                    );
                }

                cleanupTempFiles(sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to handle session error for session: {}", sessionId, e);
        }
    }

    private boolean isSessionCancelled(String sessionId) {
        return sessionRepository.findStatusById(sessionId)
                .map(status -> status == UploadSessionStatus.CANCELLED)
                .orElse(false);
    }

    private boolean persistSessionProgressIfNotCancelled(String sessionId, UploadSession session) {
        int updatedRows = sessionRepository.updateProgressIfStatusNot(
                sessionId,
                session.getProcessedFiles(),
                session.getFailedFiles(),
                session.getProcessedBytes(),
                LocalDateTime.now(),
                UploadSessionStatus.CANCELLED
        );
        if (updatedRows > 0) {
            return true;
        }
        stopProcessingIfCancelled(sessionId, session);
        return false;
    }

    private boolean stopProcessingIfCancelled(String sessionId, UploadSession session) {
        if (!isSessionCancelled(sessionId)) {
            return false;
        }
        if (session != null) {
            session.setStatus(UploadSessionStatus.CANCELLED);
            if (session.getCompletedAt() == null) {
                session.setCompletedAt(LocalDateTime.now());
            }
            releaseSessionReservationIfNeeded(session, true);
            sessionRepository.save(session);
        }
        emitSessionState(sessionId, "cancelled");
        return true;
    }

    private void releaseSessionReservationIfNeeded(UploadSession session, boolean syncUsage) {
        if (session == null || session.isQuotaReservationReleased() || session.getReservedBytes() <= 0) {
            return;
        }

        if (syncUsage) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(session.getWorkspaceId(), session.getReservedBytes());
        } else {
            workspaceQuotaGuardService.releaseReservation(session.getWorkspaceId(), session.getReservedBytes());
        }

        session.setQuotaReservationReleased(true);
        session.setReservedBytes(0L);
    }

    private void cleanupTempFiles(String sessionId) {
        try {
            Path sessionDir = uploadProperties.getTempDirectory().resolve(sessionId);
            if (Files.exists(sessionDir)) {
                Files.walk(sessionDir)
                        .sorted((a, b) -> b.compareTo(a))
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Failed to cleanup temp files for session: {}", sessionId, e);
        }
    }

    private void cleanupTempFilesExceptConflicts(String sessionId, List<UploadSessionFile> conflictFiles) {
        Set<String> conflictPaths = conflictFiles.stream()
                .map(UploadSessionFile::getTempFilePath)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        try {
            Path sessionDir = uploadProperties.getTempDirectory().resolve(sessionId);
            if (Files.exists(sessionDir)) {
                // Get all files to delete (excluding conflict files)
                List<Path> pathsToDelete = Files.walk(sessionDir)
                        .filter(path -> {
                            String pathStr = path.toAbsolutePath().toString();
                            // Keep files that are in the conflict paths or are parent directories of conflict files
                            for (String conflictPath : conflictPaths) {
                                if (conflictPath != null && (pathStr.equals(conflictPath) || conflictPath.startsWith(pathStr + "/"))) {
                                    return false;
                                }
                            }
                            return true;
                        })
                        .sorted((a, b) -> b.compareTo(a)) // Sort in reverse order (files before dirs)
                        .toList();

                for (Path path : pathsToDelete) {
                    try {
                        // Only delete if directory is empty or it's a file
                        if (Files.isRegularFile(path)) {
                            Files.delete(path);
                        } else if (Files.isDirectory(path)) {
                            try (var entries = Files.list(path)) {
                                if (entries.findAny().isEmpty()) {
                                    Files.delete(path);
                                }
                            }
                        }
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", path, e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to cleanup temp files for session: {}", sessionId, e);
        }

        if (!conflictFiles.isEmpty()) {
            log.info("Preserved {} conflict files for session {} pending resolution", conflictFiles.size(), sessionId);
        }
    }
}
