package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UploadConflictService {

    private static final Logger log = LoggerFactory.getLogger(UploadConflictService.class);

    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final UploadSessionFileRepository uploadSessionFileRepository;
    private final PageFilterIndexService pageFilterIndexService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;

    public UploadConflictService(PageRepository pageRepository,
                                  PageImageRepository pageImageRepository,
                                  PageXmlRepository pageXmlRepository,
                                  UploadSessionFileRepository uploadSessionFileRepository,
                                  PageFilterIndexService pageFilterIndexService,
                                  HierarchicalFileStorageService hierarchicalFileStorageService,
                                  PageXmlCanonicalizationService pageXmlCanonicalizationService) {
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.uploadSessionFileRepository = uploadSessionFileRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
    }

    public List<UploadConflictDto.ConflictResponse> getProjectConflicts(String projectId, String userId) {
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
                    file.getTempFilePath(),
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

    public void resolveConflicts(String projectId, String userId, UploadConflictDto.BatchConflictResolutionRequest request) {
        for (UploadConflictDto.ConflictResolution resolution : request.resolutions()) {
            Optional<UploadSessionFile> dbConflictOpt = uploadSessionFileRepository.findById(resolution.conflictId());
            if (dbConflictOpt.isPresent()) {
                UploadSessionFile dbConflict = dbConflictOpt.get();
                if (dbConflict.getStatus() == UploadFileStatus.CONFLICT) {
                    try {
                        resolveDbConflict(projectId, dbConflict, resolution.action());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to resolve database conflict: " + resolution.conflictId(), e);
                    }
                }
            }
        }
    }

    private void resolveDbConflict(String projectId, UploadSessionFile file, String action) throws IOException {
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

                // Find or create the page
                Optional<Page> pageOpt = pageRepository.findByProjectIdAndName(projectId, file.getBaseName());
                if (pageOpt.isEmpty()) {
                    log.warn("Page not found for conflict resolution: {}", file.getBaseName());
                    file.setStatus(UploadFileStatus.FAILED);
                    file.setErrorMessage("Page not found");
                    uploadSessionFileRepository.save(file);
                    return;
                }
                Page page = pageOpt.get();

                if ("IMAGE_VARIANT_EXISTS".equals(file.getConflictType())) {
                    // Find and delete existing image with same variant
                    List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(page.getId(), file.getVariant());
                    for (PageImage existingImage : existingImages) {
                        hierarchicalFileStorageService.deleteStoredFile(existingImage.getFilePath());
                        if (existingImage.getThumbnailPath() != null) {
                            hierarchicalFileStorageService.deleteStoredFile(existingImage.getThumbnailPath());
                        }
                        pageImageRepository.delete(existingImage);
                    }

                    String createdBy = file.getSession() != null ? file.getSession().getUserId() : "system";
                    var storedImage = hierarchicalFileStorageService.storeFromPath(
                            tempFilePath,
                            file.getOriginalFileName(),
                            file.getMimeType(),
                            page.getProject().getLibrary().getWorkspaceId(),
                            page.getProject().getId(),
                            StoredFileType.IMG,
                            createdBy,
                            true
                    );

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
                    pageImageRepository.save(newImage);

                } else if ("XML_FILE_EXISTS".equals(file.getConflictType())) {
                    pageXmlRepository.findByPage_Id(page.getId()).ifPresent(existingXml -> {
                        hierarchicalFileStorageService.deleteStoredFile(existingXml.getFilePath());
                        pageXmlRepository.delete(existingXml);
                        pageXmlRepository.flush();
                    });

                    String createdBy = file.getSession() != null ? file.getSession().getUserId() : "system";
                    var storedXml = hierarchicalFileStorageService.storeFromPath(
                            tempFilePath,
                            file.getOriginalFileName(),
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

                    // Index the page content for filtering
                    try {
                        pageFilterIndexService.indexPageFromXml(page);
                    } catch (Exception e) {
                        log.warn("Failed to index page {} after XML conflict resolution: {}", page.getId(), e.getMessage());
                    }
                }

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

    public boolean hasUnresolvedConflicts(String projectId) {
        return uploadSessionFileRepository.existsByProjectIdAndStatus(projectId, UploadFileStatus.CONFLICT);
    }
}
