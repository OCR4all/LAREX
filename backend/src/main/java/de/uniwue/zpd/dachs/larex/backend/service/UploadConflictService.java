package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

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
    private final Map<String, PendingConflict> pendingConflicts = new HashMap<>();

    public UploadConflictService(PageRepository pageRepository,
                                  PageImageRepository pageImageRepository,
                                  PageXmlRepository pageXmlRepository,
                                  UploadSessionFileRepository uploadSessionFileRepository,
                                  PageFilterIndexService pageFilterIndexService,
                                  HierarchicalFileStorageService hierarchicalFileStorageService) {
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.uploadSessionFileRepository = uploadSessionFileRepository;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
    }

    public List<UploadConflictDto.ConflictResponse> checkForConflicts(Page existingPage, List<MultipartFile> newFiles) {
        List<UploadConflictDto.ConflictResponse> conflicts = new ArrayList<>();

        for (MultipartFile newFile : newFiles) {
            String filename = newFile.getOriginalFilename();
            if (filename == null) continue;

            if (isImageFile(newFile)) {
                ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(filename);
                
                // Check if an image with same variant already exists for this page
                List<PageImage> existingImages = pageImageRepository.findByPageIdAndVariant(
                        existingPage.getId(), nameInfo.variant());

                if (!existingImages.isEmpty()) {
                    PageImage existingImage = existingImages.get(0);
                    String conflictId = UUID.randomUUID().toString();

                    UploadConflictDto.ConflictDetails details = new UploadConflictDto.ConflictDetails(
                            String.valueOf(existingImage.getFileSize()),
                            String.valueOf(newFile.getSize()),
                            existingImage.getUpdated(),
                            LocalDateTime.now(),
                            null,
                            null
                    );

                    UploadConflictDto.ConflictResponse conflict = new UploadConflictDto.ConflictResponse(
                            conflictId,
                            "IMAGE_VARIANT_EXISTS",
                            existingImage.getFileName(),
                            filename,
                            existingImage.getFilePath(),
                            null,
                            LocalDateTime.now(),
                            existingPage.getId(),
                            existingPage.getName(),
                            details
                    );

                    conflicts.add(conflict);
                    pendingConflicts.put(conflictId, new PendingConflict(conflict, newFile, existingImage));
                }
            } else if (isXmlFile(newFile)) {
                List<PageXml> existingXmlFiles = pageXmlRepository.findByPage_Id(existingPage.getId());
                if (!existingXmlFiles.isEmpty()) {
                    PageXml existingXml = existingXmlFiles.get(0);
                    String conflictId = UUID.randomUUID().toString();

                    UploadConflictDto.ConflictDetails details = new UploadConflictDto.ConflictDetails(
                            null,
                            String.valueOf(newFile.getSize()),
                            existingPage.getUpdated(),
                            LocalDateTime.now(),
                            null,
                            null
                    );

                    UploadConflictDto.ConflictResponse conflict = new UploadConflictDto.ConflictResponse(
                            conflictId,
                            "XML_FILE_EXISTS",
                            existingXml.getFileName(),
                            filename,
                            existingXml.getFilePath(),
                            null,
                            LocalDateTime.now(),
                            existingPage.getId(),
                            existingPage.getName(),
                            details
                    );

                    conflicts.add(conflict);
                    pendingConflicts.put(conflictId, new PendingConflict(conflict, newFile, null));
                }
            }
        }

        return conflicts;
    }

    public List<UploadConflictDto.ConflictResponse> getProjectConflicts(String projectId, String userId) {
        List<UploadConflictDto.ConflictResponse> conflicts = new ArrayList<>();

        // Get conflicts from in-memory map (synchronous uploads)
        List<PendingConflict> memoryConflicts = new ArrayList<>(pendingConflicts.values());
        Set<String> memoryPageIds = memoryConflicts.stream()
                .map(pc -> pc.conflict.pageId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Page> pagesById = memoryPageIds.isEmpty()
                ? Map.of()
                : pageRepository.findAllByIdIn(memoryPageIds).stream()
                .collect(java.util.stream.Collectors.toMap(Page::getId, page -> page));

        for (PendingConflict pending : memoryConflicts) {
            Page page = pending.conflict.pageId() != null ? pagesById.get(pending.conflict.pageId()) : null;
            if (page != null && projectId.equals(page.getProject().getId())) {
                conflicts.add(pending.conflict);
            }
        }

        // Get conflicts from database (chunked uploads)
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
            // First try to resolve from in-memory map (synchronous uploads)
            PendingConflict pending = pendingConflicts.get(resolution.conflictId());
            if (pending != null) {
                try {
                    switch (resolution.action()) {
                        case "REPLACE", "replace_with_new" -> replaceFile(pending, userId);
                        case "KEEP_EXISTING", "keep_existing", "skip" -> { /* Do nothing */ }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to resolve conflict: " + resolution.conflictId(), e);
                }
                pendingConflicts.remove(resolution.conflictId());
                continue;
            }

            // Try to resolve from database (chunked uploads)
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
                    // Delete existing XML files
                    List<PageXml> existingXmlFiles = pageXmlRepository.findByPage_Id(page.getId());
                    for (PageXml existingXml : existingXmlFiles) {
                        hierarchicalFileStorageService.deleteStoredFile(existingXml.getFilePath());
                        pageXmlRepository.delete(existingXml);
                    }

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
                    pageXmlRepository.save(pageXml);

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
        // Check in-memory conflicts (synchronous uploads)
        Set<String> pageIds = pendingConflicts.values().stream()
                .map(pc -> pc.conflict.pageId())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        boolean hasMemoryConflicts = !pageIds.isEmpty() && pageRepository.findAllByIdIn(pageIds).stream()
                .anyMatch(page -> projectId.equals(page.getProject().getId()));

        if (hasMemoryConflicts) {
            return true;
        }

        // Check database conflicts (chunked uploads)
        return uploadSessionFileRepository.existsByProjectIdAndStatus(projectId, UploadFileStatus.CONFLICT);
    }

    private void replaceFile(PendingConflict pending, String userId) throws IOException {
        Page page = pageRepository.findById(pending.conflict.pageId())
                .orElseThrow(() -> new RuntimeException("Page not found"));

        if (pending.existingImage != null) {
            // Delete old image file
            hierarchicalFileStorageService.deleteStoredFile(pending.existingImage.getFilePath());
            if (pending.existingImage.getThumbnailPath() != null) {
                hierarchicalFileStorageService.deleteStoredFile(pending.existingImage.getThumbnailPath());
            }

            // Save new image file
            String filename = pending.newFile.getOriginalFilename();
            ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(filename);
            var storedImage = hierarchicalFileStorageService.storeMultipartFile(
                    pending.newFile,
                    page.getProject().getLibrary().getWorkspaceId(),
                    page.getProject().getId(),
                    StoredFileType.IMG,
                    userId
            );

            // Update existing PageImage entity
            pending.existingImage.setFileName(storedImage.originalFilename());
            pending.existingImage.setFilePath(storedImage.storagePath());
            pending.existingImage.setMimeType(storedImage.mimeType());
            pending.existingImage.setFileSize(storedImage.sizeBytes());
            pending.existingImage.setVariant(nameInfo.variant());
            pending.existingImage.setBaseName(nameInfo.baseName());
            pageImageRepository.save(pending.existingImage);

        } else {
            // XML file replacement - delete existing XML files
            List<PageXml> existingXmlFiles = pageXmlRepository.findByPage_Id(page.getId());
            for (PageXml existingXml : existingXmlFiles) {
                hierarchicalFileStorageService.deleteStoredFile(existingXml.getFilePath());
                pageXmlRepository.delete(existingXml);
            }

            var storedXml = hierarchicalFileStorageService.storeMultipartFile(
                    pending.newFile,
                    page.getProject().getLibrary().getWorkspaceId(),
                    page.getProject().getId(),
                    StoredFileType.XML,
                    userId
            );

            String baseName = (storedXml.originalFilename() != null && storedXml.originalFilename().contains("."))
                    ? storedXml.originalFilename().substring(0, storedXml.originalFilename().lastIndexOf('.'))
                    : storedXml.originalFilename();

            PageXml pageXml = new PageXml(
                    storedXml.originalFilename(),
                    storedXml.storagePath(),
                    storedXml.mimeType(),
                    storedXml.sizeBytes(),
                    "original",
                    baseName,
                    XmlSchema.PAGE_XML, null, page
            );
            pageXmlRepository.save(pageXml);

            // Index the page content for filtering
            try {
                pageFilterIndexService.indexPageFromXml(page);
            } catch (Exception e) {
                log.warn("Failed to index page {} after XML conflict resolution: {}", page.getId(), e.getMessage());
            }
        }
    }

    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private boolean isXmlFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".xml");
    }

    private record PendingConflict(
            UploadConflictDto.ConflictResponse conflict,
            MultipartFile newFile,
            PageImage existingImage
    ) {}
}
