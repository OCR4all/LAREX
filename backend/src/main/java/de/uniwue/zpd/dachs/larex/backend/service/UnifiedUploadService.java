package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.dto.UploadConflictDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class UnifiedUploadService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedUploadService.class);

    private final ProjectRepository projectRepository;
    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final UploadConflictService uploadConflictService;
    private final PageFilterIndexService pageFilterIndexService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;

    public UnifiedUploadService(ProjectRepository projectRepository,
                               PageRepository pageRepository,
                               PageImageRepository pageImageRepository,
                               PageXmlRepository pageXmlRepository,
                               UploadConflictService uploadConflictService,
                               PageFilterIndexService pageFilterIndexService,
                               HierarchicalFileStorageService hierarchicalFileStorageService) {
        this.projectRepository = projectRepository;
        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.uploadConflictService = uploadConflictService;
        this.pageFilterIndexService = pageFilterIndexService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
    }

    public UploadConflictDto.UploadResponse processUpload(String projectId, List<MultipartFile> filesList, String userId) {
        MultipartFile[] files = filesList.toArray(new MultipartFile[0]);
        return processUploadInternal(projectId, files, userId);
    }

    private UploadConflictDto.UploadResponse processUploadInternal(String projectId, MultipartFile[] files, String userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // Group files by basename
        Map<String, List<MultipartFile>> filesByBasename = Arrays.stream(files)
                .collect(Collectors.groupingBy(this::extractBasename));
        Map<String, Page> existingByBaseName = pageRepository.findByProjectIdAndNameIn(projectId, filesByBasename.keySet())
                .stream()
                .collect(Collectors.toMap(Page::getName, page -> page));

        List<UploadConflictDto.ConflictResponse> conflicts = new ArrayList<>();
        List<String> processedPages = new ArrayList<>();
        int pagesCreated = 0;
        int pagesUpdated = 0;
        int imagesProcessed = 0;
        int xmlFilesProcessed = 0;

        for (Map.Entry<String, List<MultipartFile>> entry : filesByBasename.entrySet()) {
            String basename = entry.getKey();
            List<MultipartFile> groupFiles = entry.getValue();

            Page existingPage = existingByBaseName.get(basename);

            if (existingPage != null) {
                // Check for conflicts on existing page
                List<UploadConflictDto.ConflictResponse> pageConflicts =
                    uploadConflictService.checkForConflicts(existingPage, groupFiles);

                if (!pageConflicts.isEmpty()) {
                    conflicts.addAll(pageConflicts);
                    continue;
                }

                // No conflicts, update existing page
                int[] counts = processFilesForPage(existingPage, groupFiles, userId);
                imagesProcessed += counts[0];
                xmlFilesProcessed += counts[1];
                pagesUpdated++;
            } else {
                // Create new page
                Page newPage = createPage(project, basename);
                existingByBaseName.put(basename, newPage);
                int[] counts = processFilesForPage(newPage, groupFiles, userId);
                imagesProcessed += counts[0];
                xmlFilesProcessed += counts[1];
                pagesCreated++;
            }

            processedPages.add(basename);
        }

        UploadConflictDto.UploadResultDto result = new UploadConflictDto.UploadResultDto(
                pagesCreated, pagesUpdated, imagesProcessed, xmlFilesProcessed, processedPages);

        return new UploadConflictDto.UploadResponse(!conflicts.isEmpty(), conflicts, result);
    }

    private String extractBasename(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) return "unknown";
        ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(filename);
        return nameInfo.baseName();
    }

    private Page createPage(Project project, String basename) {
        Page page = new Page();
        page.setProject(project);
        page.setName(basename);
        page.setDescription("Auto-created from upload");
        page.setTags(new ArrayList<>());
        page.setCreated(LocalDateTime.now());
        page.setUpdated(LocalDateTime.now());
        return pageRepository.save(page);
    }

    private int[] processFilesForPage(Page page, List<MultipartFile> files, String createdByUserId) {
        int imagesProcessed = 0;
        int xmlFilesProcessed = 0;
        String workspaceId = page.getProject().getLibrary().getWorkspaceId();
        String projectId = page.getProject().getId();

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) continue;

            try {
                if (isImageFile(file)) {
                    var storedImage = hierarchicalFileStorageService.storeMultipartFile(
                            file,
                            workspaceId,
                            projectId,
                            StoredFileType.IMG,
                            createdByUserId
                    );
                    ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(storedImage.originalFilename());

                    PageImage pageImage = new PageImage(
                            storedImage.originalFilename(),
                            storedImage.storagePath(),
                            storedImage.mimeType(),
                            storedImage.sizeBytes(),
                            nameInfo.variant(),
                            nameInfo.baseName(),
                            page
                    );
                    pageImageRepository.save(pageImage);
                    imagesProcessed++;

                } else if (isXmlFile(file)) {
                    var storedXml = hierarchicalFileStorageService.storeMultipartFile(
                            file,
                            workspaceId,
                            projectId,
                            StoredFileType.XML,
                            createdByUserId
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
                        log.warn("Failed to index page {} after XML upload: {}", page.getId(), e.getMessage());
                    }

                    xmlFilesProcessed++;
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to save file: " + filename, e);
            }
        }

        page.setUpdated(LocalDateTime.now());
        pageRepository.save(page);

        return new int[]{imagesProcessed, xmlFilesProcessed};
    }

    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private boolean isXmlFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        return filename != null && filename.toLowerCase().endsWith(".xml");
    }

    private boolean isImageFileName(String filename) {
        if (filename == null) return false;
        String lowerName = filename.toLowerCase();
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
               lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") ||
               lowerName.endsWith(".bmp") || lowerName.endsWith(".tiff") ||
               lowerName.endsWith(".webp");
    }

    public Map<String, Object> bulkUploadImages(String projectId, List<MultipartFile> files, String userId) throws IOException {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        Map<String, List<MultipartFile>> groupedFiles = files.stream()
                .collect(Collectors.groupingBy(file -> {
                    ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(file.getOriginalFilename());
                    return nameInfo.baseName();
                }));
        Map<String, Page> pagesByLowerName = pageRepository.findByProjectIdAndLowerNameIn(
                        projectId,
                        groupedFiles.keySet().stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(page -> page.getName().toLowerCase(Locale.ROOT), page -> page));

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> createdPages = new ArrayList<>();
        int totalImagesProcessed = 0;

        for (Map.Entry<String, List<MultipartFile>> entry : groupedFiles.entrySet()) {
            String baseName = entry.getKey();
            List<MultipartFile> imageFiles = entry.getValue();

            String pageName = baseName;
            Page existingPage = pagesByLowerName.get(pageName.toLowerCase(Locale.ROOT));

            Page page;
            boolean pageCreated = false;

            if (existingPage == null) {
                page = new Page(pageName, "Auto-created from bulk image upload", project);
                page = pageRepository.save(page);
                pagesByLowerName.put(pageName.toLowerCase(Locale.ROOT), page);
                pageCreated = true;
            } else {
                page = existingPage;
            }

            List<String> uploadedImages = new ArrayList<>();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
            String pageProjectId = page.getProject().getId();

            for (MultipartFile imageFile : imageFiles) {
                String filename = imageFile.getOriginalFilename();
                if (filename != null && !filename.isEmpty()) {
                    var storedImage = hierarchicalFileStorageService.storeMultipartFile(
                            imageFile,
                            workspaceId,
                            pageProjectId,
                            StoredFileType.IMG,
                            userId
                    );
                    ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(storedImage.originalFilename());

                    PageImage pageImage = new PageImage(
                            storedImage.originalFilename(),
                            storedImage.storagePath(),
                            storedImage.mimeType(),
                            storedImage.sizeBytes(),
                            nameInfo.variant(),
                            nameInfo.baseName(),
                            page
                    );

                    pageImageRepository.save(pageImage);
                    uploadedImages.add(storedImage.originalFilename());
                    totalImagesProcessed++;
                }
            }

            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("pageId", page.getId());
            pageInfo.put("pageName", pageName);
            pageInfo.put("created", pageCreated);
            pageInfo.put("imagesCount", uploadedImages.size());
            pageInfo.put("uploadedImages", uploadedImages);

            createdPages.add(pageInfo);
        }

        result.put("success", true);
        result.put("totalImages", totalImagesProcessed);
        result.put("totalBaseNames", groupedFiles.size());
        result.put("pages", createdPages);

        return result;
    }

    public Map<String, Object> importDataset(String projectId, List<MultipartFile> files, String userId) throws IOException {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (files.isEmpty()) {
            throw new IllegalArgumentException("No files provided");
        }

        Map<String, List<MultipartFile>> imageGroups = new HashMap<>();
        Map<String, MultipartFile> xmlFiles = new HashMap<>();

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) continue;

            if (filename.toLowerCase().endsWith(".xml")) {
                String baseName = filename.substring(0, filename.lastIndexOf(".xml"));
                xmlFiles.put(baseName, file);
            } else if (isImageFileName(filename)) {
                ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(filename);
                imageGroups.computeIfAbsent(nameInfo.baseName(), k -> new ArrayList<>()).add(file);
            }
        }

        Set<String> allBaseNames = new HashSet<>();
        allBaseNames.addAll(imageGroups.keySet());
        allBaseNames.addAll(xmlFiles.keySet());
        Map<String, Page> pagesByLowerName = pageRepository.findByProjectIdAndLowerNameIn(
                        projectId,
                        allBaseNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(page -> page.getName().toLowerCase(Locale.ROOT), page -> page));

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> createdPages = new ArrayList<>();
        int totalImagesProcessed = 0;
        int totalXmlFilesProcessed = 0;

        for (String baseName : allBaseNames) {
            String pageName = baseName;
            Page existingPage = pagesByLowerName.get(pageName.toLowerCase(Locale.ROOT));

            Page page;
            boolean pageCreated = false;

            if (existingPage == null) {
                page = new Page(pageName, "Auto-created from dataset import", project);
                page = pageRepository.save(page);
                pagesByLowerName.put(pageName.toLowerCase(Locale.ROOT), page);
                pageCreated = true;
            } else {
                page = existingPage;
            }

            List<String> uploadedImages = new ArrayList<>();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
            String pageProjectId = page.getProject().getId();
            List<MultipartFile> pageImages = imageGroups.get(baseName);
            if (pageImages != null) {
                for (MultipartFile imageFile : pageImages) {
                    String filename = imageFile.getOriginalFilename();
                    if (filename != null && !filename.isEmpty()) {
                        var storedImage = hierarchicalFileStorageService.storeMultipartFile(
                                imageFile,
                                workspaceId,
                                pageProjectId,
                                StoredFileType.IMG,
                                userId
                        );
                        ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(storedImage.originalFilename());

                        PageImage pageImage = new PageImage(
                                storedImage.originalFilename(),
                                storedImage.storagePath(),
                                storedImage.mimeType(),
                                storedImage.sizeBytes(),
                                nameInfo.variant(),
                                nameInfo.baseName(),
                                page
                        );

                        pageImageRepository.save(pageImage);
                        uploadedImages.add(storedImage.originalFilename());
                        totalImagesProcessed++;
                    }
                }
            }

            MultipartFile xmlFile = xmlFiles.get(baseName);
            boolean xmlUploaded = false;
            if (xmlFile != null) {
                String filename = xmlFile.getOriginalFilename();
                if (filename != null && !filename.isEmpty()) {
                    var storedXml = hierarchicalFileStorageService.storeMultipartFile(
                            xmlFile,
                            workspaceId,
                            pageProjectId,
                            StoredFileType.XML,
                            userId
                    );
                    String xmlBaseName = (storedXml.originalFilename().contains("."))
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
                    pageXmlRepository.save(pageXml);

                    // Index the page content for filtering
                    try {
                        pageFilterIndexService.indexPageFromXml(page);
                    } catch (Exception e) {
                        log.warn("Failed to index page {} after XML upload: {}", page.getId(), e.getMessage());
                    }

                    xmlUploaded = true;
                    totalXmlFilesProcessed++;
                }
            }

            Map<String, Object> pageInfo = new HashMap<>();
            pageInfo.put("pageName", pageName);
            pageInfo.put("pageId", page.getId());
            pageInfo.put("pageCreated", pageCreated);
            pageInfo.put("imagesUploaded", uploadedImages.size());
            pageInfo.put("imageFilenames", uploadedImages);
            pageInfo.put("xmlUploaded", xmlUploaded);
            createdPages.add(pageInfo);
        }

        result.put("totalPages", createdPages.size());
        result.put("totalImagesProcessed", totalImagesProcessed);
        result.put("totalXmlFilesProcessed", totalXmlFilesProcessed);
        result.put("pages", createdPages);

        return result;
    }
}
