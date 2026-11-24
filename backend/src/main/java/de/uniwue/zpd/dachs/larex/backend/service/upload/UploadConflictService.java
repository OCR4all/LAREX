package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.LinkedDatasetItemSync;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageWriteGuard;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.OcrFormatToPageXml;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlValidationService;
import org.springframework.core.io.FileSystemResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional
public class UploadConflictService {

    private static final Logger log = LoggerFactory.getLogger(UploadConflictService.class);

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final UploadSessionFileRepository uploadSessionFileRepository;
    private final PageFilterIndexService pageFilterIndexService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final PageXmlValidationService pageXmlValidationService;
    private final LinkedDatasetItemSync linkedDatasetItemSync;
    private final PageWriteGuard pageWriteGuard;
    private final WorkspaceAccessService workspaceAccessService;
    private final TransactionTemplate cleanupTransaction;

    public UploadConflictService(ProjectRepository projectRepository,
                                  PageRepository pageRepository,
                                  PageImageRepository pageImageRepository,
                                  PageXmlRepository pageXmlRepository,
                                  UploadSessionFileRepository uploadSessionFileRepository,
                                  PageFilterIndexService pageFilterIndexService,
                                  HierarchicalFileStorageService hierarchicalFileStorageService,
                                  PageXmlCanonicalizationService pageXmlCanonicalizationService,
                                  PageXmlValidationService pageXmlValidationService,
                                  LinkedDatasetItemSync linkedDatasetItemSync,
                                  PageWriteGuard pageWriteGuard,
                                  WorkspaceAccessService workspaceAccessService,
                                  PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.uploadSessionFileRepository = uploadSessionFileRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.pageXmlValidationService = pageXmlValidationService;
        this.linkedDatasetItemSync = linkedDatasetItemSync;
        this.pageWriteGuard = pageWriteGuard;
        this.workspaceAccessService = workspaceAccessService;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public List<UploadConflictDto.ConflictResponse> getProjectConflicts(String workspaceId, String projectId, String userId) {
        requireReadableProject(workspaceId, projectId, userId);
        List<UploadConflictDto.ConflictResponse> conflicts = new ArrayList<>();

        List<UploadSessionFile> dbConflicts = uploadSessionFileRepository.findByProjectIdAndStatus(
                projectId, UploadFileStatus.CONFLICT);
        Map<String, Page> pagesByName = dbConflicts.isEmpty()
                ? Map.of()
                : pageRepository.findByProjectIdAndNameIn(
                projectId,
                dbConflicts.stream().map(UploadSessionFile::getBaseName).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
        ).stream().collect(java.util.stream.Collectors.toMap(Page::getName, page -> page));

        for (UploadSessionFile file : dbConflicts) {
            // Find the page that this file would belong to
            Page existingPage = pagesByName.get(file.getBaseName());
            String pageId = existingPage != null ? existingPage.getId() : null;
            String pageName = existingPage != null ? existingPage.getName() : file.getBaseName();

            UploadConflictDto.ConflictDetails details = new UploadConflictDto.ConflictDetails(
                    null, // existing file size not readily available
                    String.valueOf(file.getFileSize()),
                    existingPage != null ? existingPage.getUpdated() : null,
                    file.getCreated(),
                    null,
                    null
            );

            UploadConflictDto.ConflictResponse conflict = new UploadConflictDto.ConflictResponse(
                    file.getId(), // Use the file ID as conflict ID for database conflicts
                    file.getConflictType(),
                    file.getOriginalFileName(),
                    file.getOriginalFileName(),
                    null,
                    file.getCreated(),
                    pageId,
                    pageName,
                    details
            );

            conflicts.add(conflict);
        }

        return conflicts;
    }

    public UploadConflictDto.BatchConflictResolutionResponse resolveConflicts(String workspaceId, String projectId, String userId,
                                                                              UploadConflictDto.BatchConflictResolutionRequest request) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);
        requireProjectInWorkspace(workspaceId, projectId);
        Map<UploadConflictDto.ConflictResolution, UploadSessionFile> conflicts = new LinkedHashMap<>();
        for (UploadConflictDto.ConflictResolution resolution : request.resolutions()) {
            UploadSessionFile file = uploadSessionFileRepository.findByIdAndProjectId(resolution.conflictId(), projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Upload conflict", resolution.conflictId()));
            if (file.getStatus() == UploadFileStatus.CONFLICT) {
                conflicts.put(resolution, file);
            }
        }

        Map<String, Page> pagesByName = new HashMap<>();
        conflicts.forEach((resolution, file) -> {
            if (isReplace(resolution.action()) && !pagesByName.containsKey(file.getBaseName())) {
                pageRepository.findByProjectIdAndName(projectId, file.getBaseName())
                        .ifPresent(page -> pagesByName.put(file.getBaseName(), page));
            }
        });
        Map<String, String> lockReasons = lockPages(pagesByName.values(), userId);

        List<UploadConflictDto.SkippedConflict> skipped = new ArrayList<>();
        conflicts.forEach((resolution, file) -> {
            if (file.getStatus() != UploadFileStatus.CONFLICT) {
                return;
            }
            Page page = pagesByName.get(file.getBaseName());
            String lockReason = page == null || !isReplace(resolution.action()) ? null : lockReasons.get(page.getId());
            try {
                if (lockReason == null) {
                    resolveDbConflict(page, file, resolution.action());
                } else {
                    skipLockedFile(file, lockReason);
                    skipped.add(new UploadConflictDto.SkippedConflict(
                            file.getId(), file.getOriginalFileName(), page.getId(), page.getName(), lockReason));
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to resolve database conflict: " + resolution.conflictId(), e);
            }
        });
        return new UploadConflictDto.BatchConflictResolutionResponse(skipped);
    }

    private Map<String, String> lockPages(Collection<Page> pages, String userId) {
        if (pages.isEmpty()) {
            return Map.of();
        }
        String workspaceId = pages.iterator().next().getProject().getLibrary().getWorkspaceId();
        return pageWriteGuard.lockPages(
                pages.stream().map(Page::getId).toList(),
                workspaceAccessService.canReviewPages(workspaceId, userId)
        );
    }

    private boolean isReplace(String action) {
        return "REPLACE".equals(action) || "replace_with_new".equals(action);
    }

    private void skipLockedFile(UploadSessionFile file, String lockReason) throws IOException {
        if (file.getTempFilePath() != null) {
            Files.deleteIfExists(Paths.get(file.getTempFilePath()));
        }
        file.setStatus(UploadFileStatus.SKIPPED);
        file.setErrorMessage(lockReason);
        uploadSessionFileRepository.save(file);
    }

    private void resolveDbConflict(Page page, UploadSessionFile file, String action) throws IOException {
        Path tempFilePath = file.getTempFilePath() != null ? Paths.get(file.getTempFilePath()) : null;

        switch (action) {
            case "REPLACE", "replace_with_new" -> {
                if (tempFilePath == null || !Files.exists(tempFilePath)) {
                    log.warn("Temp file not found for conflict resolution: {}", file.getId());
                    file.setStatus(UploadFileStatus.FAILED);
                    file.setErrorMessage("Temp file not found for conflict resolution");
                    uploadSessionFileRepository.save(file);
                    return;
                }

                if (page == null) {
                    log.warn("Page not found for conflict resolution: {}", file.getBaseName());
                    file.setStatus(UploadFileStatus.FAILED);
                    file.setErrorMessage("Page not found");
                    uploadSessionFileRepository.save(file);
                    return;
                }
                List<String> replacedStoragePaths = new ArrayList<>();

                if ("IMAGE_VARIANT_EXISTS".equals(file.getConflictType())) {
                    // Remove the old database rows now, but keep their bytes until this
                    // replacement transaction has committed successfully.
                    List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(page.getId(), file.getVariant());


                    String createdBy = file.getSession() != null ? file.getSession().getUserId() : "system";
                    var storedImage = hierarchicalFileStorageService.storeFromPath(
                            tempFilePath,
                            OcrFormatToPageXml.pageXmlFileName(file.getOriginalFileName()),
                            file.getMimeType(),
                            page.getProject().getLibrary().getWorkspaceId(),
                            page.getProject().getId(),
                            StoredFileType.IMG,
                            createdBy,
                            true
                    );

                    for (PageImage existingImage : existingImages) {
                        replacedStoragePaths.add(existingImage.getFilePath());
                        if (existingImage.getThumbnailPath() != null) {
                            replacedStoragePaths.add(existingImage.getThumbnailPath());
                        }
                        pageImageRepository.delete(existingImage);
                    }

                    // Create new PageImage
                    PageImage newImage = new PageImage(
                            storedImage.originalFilename(),
                            storedImage.storagePath(),
                            storedImage.mimeType(),
                            storedImage.sizeBytes(),
                            file.getVariant(),
                            file.getBaseName(),
                            page
                    );
                    newImage.setImageType(storedImage.imageType());
                    pageImageRepository.save(newImage);

                } else if ("XML_FILE_EXISTS".equals(file.getConflictType())) {
                    Optional<byte[]> converted = pageXmlValidationService.toImportablePageXml(new FileSystemResource(tempFilePath),
                            imageFilename -> pageXmlCanonicalizationService.storedPageImage(page.getId()).apply(imageFilename));
                    if (converted.isPresent()) {
                        OcrFormatToPageXml.writeAtomically(tempFilePath, converted.get());
                    }
                    pageXmlRepository.findByPage_Id(page.getId()).ifPresent(existingXml -> {
                        replacedStoragePaths.add(existingXml.getFilePath());
                        pageXmlRepository.delete(existingXml);
                        pageXmlRepository.flush();
                    });

                    String createdBy = file.getSession() != null ? file.getSession().getUserId() : "system";
                    var storedXml = hierarchicalFileStorageService.storeFromPath(
                            tempFilePath,
                            OcrFormatToPageXml.pageXmlFileName(file.getOriginalFileName()),
                            file.getMimeType(),
                            page.getProject().getLibrary().getWorkspaceId(),
                            page.getProject().getId(),
                            StoredFileType.XML,
                            createdBy,
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
                    pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, createdBy, "upload conflict replacement");
                    linkedDatasetItemSync.relink(pageXml);

                    // Index the page content for filtering
                    try {
                        pageFilterIndexService.indexPageFromXml(page);
                    } catch (Exception e) {
                        log.warn("Failed to index page {} after XML conflict resolution: {}", page.getId(), e.getMessage());
                    }
                }

                deleteStoredFilesAfterCommit(replacedStoragePaths);

                file.setStatus(UploadFileStatus.COMPLETED);
                file.setCreatedPageId(page.getId());
            }
            case "KEEP_EXISTING", "keep_existing", "skip" -> {
                // Delete temp file and mark as skipped
                if (tempFilePath != null) {
                    Files.deleteIfExists(tempFilePath);
                }
                file.setStatus(UploadFileStatus.SKIPPED);
            }
        }

        uploadSessionFileRepository.save(file);
    }

    private void deleteStoredFilesAfterCommit(Collection<String> storagePaths) {
        List<String> paths = storagePaths.stream()
                .filter(path -> path != null && !path.isBlank())
                .distinct()
                .toList();
        if (paths.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> cleanupTransaction.executeWithoutResult(
                status -> hierarchicalFileStorageService.deleteUnreferencedStoredFiles(paths));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
        }
    }

    public boolean hasUnresolvedConflicts(String workspaceId, String projectId, String userId) {
        requireReadableProject(workspaceId, projectId, userId);
        return uploadSessionFileRepository.existsByProjectIdAndStatus(projectId, UploadFileStatus.CONFLICT);
    }

    private void requireReadableProject(String workspaceId, String projectId, String userId) {
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        requireProjectInWorkspace(workspaceId, projectId);
    }

    private void requireProjectInWorkspace(String workspaceId, String projectId) {
        if (!projectRepository.existsByIdAndLibraryWorkspaceId(projectId, workspaceId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }
}
