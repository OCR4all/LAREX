package de.uniwue.zpd.dachs.larex.backend.service.page;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlAttributeIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class PageService {

    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final PageTextContentRepository pageTextContentRepository;
    private final PageLabelIndexRepository pageLabelIndexRepository;
    private final PageConfidenceIndexRepository pageConfidenceIndexRepository;
    private final PageXmlAttributeIndexRepository pageXmlAttributeIndexRepository;
    private final TaskPageLinkRepository taskPageLinkRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotificationService notificationService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ThumbnailService thumbnailService;
    private final PageXmlVersionService pageXmlVersionService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;
    private final PageXmlCanonicalizationService pageXmlCanonicalizationService;
    private final WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    private final PageOrderService pageOrderService;
    private final AnnotationReadCache annotationReadCache;
    private final PageFilterIndexService pageFilterIndexService;

    public PageService(
            PageRepository pageRepository,
            PageImageRepository pageImageRepository,
            PageXmlRepository pageXmlRepository,
            PageTextContentRepository pageTextContentRepository,
            PageLabelIndexRepository pageLabelIndexRepository,
            PageConfidenceIndexRepository pageConfidenceIndexRepository,
            PageXmlAttributeIndexRepository pageXmlAttributeIndexRepository,
            TaskPageLinkRepository taskPageLinkRepository,
            ProjectRepository projectRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            NotificationService notificationService,
            WorkspaceAccessService workspaceAccessService,
            ThumbnailService thumbnailService,
            PageXmlVersionService pageXmlVersionService,
            HierarchicalFileStorageService hierarchicalFileStorageService,
            PageXmlCanonicalizationService pageXmlCanonicalizationService,
            WorkspaceQuotaRefreshService workspaceQuotaRefreshService,
            PageOrderService pageOrderService,
            AnnotationReadCache annotationReadCache,
            PageFilterIndexService pageFilterIndexService) {

        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.pageTextContentRepository = pageTextContentRepository;
        this.pageLabelIndexRepository = pageLabelIndexRepository;
        this.pageConfidenceIndexRepository = pageConfidenceIndexRepository;
        this.pageXmlAttributeIndexRepository = pageXmlAttributeIndexRepository;
        this.taskPageLinkRepository = taskPageLinkRepository;
        this.projectRepository = projectRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.notificationService = notificationService;
        this.workspaceAccessService = workspaceAccessService;
        this.thumbnailService = thumbnailService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
        this.pageXmlCanonicalizationService = pageXmlCanonicalizationService;
        this.workspaceQuotaRefreshService = workspaceQuotaRefreshService;
        this.pageOrderService = pageOrderService;
        this.annotationReadCache = annotationReadCache;
        this.pageFilterIndexService = pageFilterIndexService;
    }

    public List<Page> getProjectPages(String projectId, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            String workspaceId = projectOpt.get().getLibrary().getWorkspaceId();
            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                return pageOrderService.sortPages(pageRepository.findByProjectId(projectId));
            }
        }
        return List.of();
    }

    public org.springframework.data.domain.Page<Page> getProjectPagesPaginated(
            String projectId,
            String search,
            List<String> tags,
            String sortField,
            int page,
            int size,
            String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isEmpty()) {
            return org.springframework.data.domain.Page.empty();
        }

        String workspaceId = projectOpt.get().getLibrary().getWorkspaceId();
        if (!workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
            return org.springframework.data.domain.Page.empty();
        }

        String safeSort = (sortField == null || sortField.isBlank()) ? "name" : sortField;
        if ("projectOrder".equalsIgnoreCase(safeSort) || "sortOrder".equalsIgnoreCase(safeSort)) {
            List<Page> sortedPages;
            if (search != null && !search.trim().isEmpty()) {
                sortedPages = pageOrderService.sortPages(
                        pageRepository.findPagesInProjectBySearch(projectId, search.trim().toLowerCase())
                );
            } else if (tags != null && !tags.isEmpty()) {
                sortedPages = pageOrderService.sortPages(pageRepository.findByProjectIdAndTagsIn(projectId, tags));
            } else {
                sortedPages = pageOrderService.sortPages(pageRepository.findByProjectId(projectId));
            }
            int from = Math.min(Math.max(page, 0) * size, sortedPages.size());
            int to = Math.min(from + size, sortedPages.size());
            return new org.springframework.data.domain.PageImpl<>(
                    sortedPages.subList(from, to),
                    PageRequest.of(page, size),
                    sortedPages.size()
            );
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, safeSort));

        if (search != null && !search.trim().isEmpty()) {
            return pageRepository.findPagesInProjectBySearch(projectId, search.trim().toLowerCase(), pageable);
        }
        if (tags != null && !tags.isEmpty()) {
            return pageRepository.findByProjectIdAndTagsIn(projectId, tags, pageable);
        }
        return pageRepository.findByProjectId(projectId, pageable);
    }

    public Optional<Page> getPageById(String pageId, String userId) {
        Optional<Page> pageOpt = pageRepository.findById(pageId);
        if (pageOpt.isPresent()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();

            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                return pageOpt;
            }
        }
        return Optional.empty();
    }

    public Optional<Page> createPage(String projectId, String name, String description, List<String> tags, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            Project project = projectOpt.get();
            String workspaceId = project.getLibrary().getWorkspaceId();

            if (workspaceAccessService.canManageProjects(workspaceId, userId)) {
                if (pageRepository.existsByNameAndProjectId(name, projectId)) {
                    throw new IllegalArgumentException("Page name '" + name + "' already exists in this project");
                }

                Page page = new Page(name, description, project);
                if (tags != null) {
                    page.setTags(tags);
                }

                page = pageRepository.save(page);

                notificationService.createPageCreatedNotification(
                        userId,
                        "New page created: " + name,
                        page.getId()
                );

                return Optional.of(page);
            }
        }
        return Optional.empty();
    }

    public Optional<Page> updatePage(String pageId, String name, String description, List<String> tags, String userId) {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
            if (!workspaceAccessService.canManageProjects(workspaceId, userId)) {
                return Optional.empty();
            }
            assertPageWritable(page);
            if (!page.getName().equals(name) && pageRepository.existsByNameAndProjectId(name, page.getProject().getId())) {
                throw new IllegalArgumentException("Page name '" + name + "' already exists in this project");
            }
            page.setName(name);
            page.setDescription(description);
            if (tags != null) {
                page.setTags(tags);
            }

            return Optional.of(pageRepository.save(page));
        }
        return Optional.empty();
    }

    public boolean deletePage(String pageId, String userId) {
        return deletePage(pageId, userId, true);
    }

    private boolean deletePage(String pageId, String userId, boolean notify) {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();

            if (workspaceAccessService.isUserAdministrator(workspaceId, userId)) {
                assertPageWritable(page);
                String pageName = page.getName();

                deletePageFiles(page);

                pageRepository.delete(page);

                if (notify) {
                    notificationService.createPageDeletedNotification(
                            userId,
                            "Page deleted: " + pageName,
                            pageId
                    );
                }

                workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);

                return true;
            }
        }
        return false;
    }

    public int deletePages(List<String> pageIds, String userId) {
        if (pageIds == null || pageIds.isEmpty()) {
            return 0;
        }

        List<String> normalizedIds = pageIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return 0;
        }

        List<Page> candidatePages = pageRepository.findAllByIdIn(normalizedIds);
        if (candidatePages.isEmpty()) {
            return 0;
        }

        Map<String, Page> candidatePagesById = candidatePages.stream()
                .collect(Collectors.toMap(Page::getId, page -> page));

        List<Page> pagesToDelete = normalizedIds.stream()
                .map(candidatePagesById::get)
                .filter(Objects::nonNull)
                .filter(page -> workspaceAccessService.isUserAdministrator(
                        page.getProject().getLibrary().getWorkspaceId(),
                        userId
                ))
                .filter(page -> !page.getProject().isLocked() && !page.isEffectivelyLocked())
                .toList();
        if (pagesToDelete.isEmpty()) {
            return 0;
        }

        String projectId = pagesToDelete.get(0).getProject().getId();
        String projectName = pagesToDelete.get(0).getProject().getName();
        List<String> deletedPageNames = pagesToDelete.stream().map(Page::getName).toList();
        List<String> pageIdsToDelete = pagesToDelete.stream().map(Page::getId).toList();

        List<PageXml> xmlFiles = pageXmlRepository.findByPage_IdIn(pageIdsToDelete);
        List<PageImage> images = pageImageRepository.findByPageIdIn(pageIdsToDelete);
        deletePageFiles(pageIdsToDelete, xmlFiles, images);

        taskPageLinkRepository.deleteByPageIdIn(pageIdsToDelete);
        pageTextContentRepository.deleteByPageIdIn(pageIdsToDelete);
        pageLabelIndexRepository.deleteByPageIdIn(pageIdsToDelete);
        pageConfidenceIndexRepository.deleteByPageIdIn(pageIdsToDelete);
        pageImageRepository.deleteByPageIdIn(pageIdsToDelete);
        pageXmlRepository.deleteByPageIdIn(pageIdsToDelete);
        pageRepository.deleteTagsByPageIds(pageIdsToDelete);
        int deletedCount = pageRepository.deleteByIdIn(pageIdsToDelete);

        if (deletedCount > 0) {
            int notificationCount = Math.min(deletedCount, deletedPageNames.size());
            notificationService.createBatchPageDeletedNotification(
                    userId,
                    deletedPageNames.subList(0, notificationCount),
                    projectId,
                    projectName
            );
            pagesToDelete.stream()
                    .map(page -> page.getProject().getLibrary().getWorkspaceId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .forEach(workspaceQuotaRefreshService::scheduleUsageRefresh);
        }

        return deletedCount;
    }

    public List<Page> searchPages(String projectId, String searchTerm, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            String workspaceId = projectOpt.get().getLibrary().getWorkspaceId();
            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                Optional<Page> pageOpt = pageRepository.findByProjectIdAndNameIgnoreCase(projectId, searchTerm);
                return pageOpt.map(List::of).orElse(List.of());
            }
        }
        return List.of();
    }

    public List<Page> getPagesByTags(String projectId, List<String> tags, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            String workspaceId = projectOpt.get().getLibrary().getWorkspaceId();
            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                return pageOrderService.sortPages(pageRepository.findByProjectIdAndTagsIn(projectId, tags));
            }
        }
        return List.of();
    }

    public boolean uploadXmlFile(String pageId, MultipartFile xmlFile, String userId) throws IOException {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent() && xmlFile != null && !xmlFile.isEmpty()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
            if (!workspaceAccessService.canManageProjects(workspaceId, userId)) {
                return false;
            }
            assertPageWritable(page);

            if (!isValidXmlFile(xmlFile)) {
                return false;
            }
            if (pageXmlRepository.existsByPage_Id(pageId)) {
                return false;
            }

            String projectId = page.getProject().getId();
            var storedFile = hierarchicalFileStorageService.storeMultipartFile(
                    xmlFile,
                    workspaceId,
                    projectId,
                    StoredFileType.XML,
                    userId
            );

            String baseName = (storedFile.originalFilename() != null && storedFile.originalFilename().contains("."))
                    ? storedFile.originalFilename().substring(0, storedFile.originalFilename().lastIndexOf('.'))
                    : storedFile.originalFilename();

            PageXml pageXml = new PageXml(
                    storedFile.originalFilename(),
                    storedFile.storagePath(),
                    storedFile.mimeType(),
                    storedFile.sizeBytes(),
                    "original",
                    baseName,
                    XmlSchema.PAGE_XML, null, page
            );
            pageXml = pageXmlRepository.save(pageXml);
            pageXmlCanonicalizationService.canonicalizeAtIngest(pageXml, userId, "direct XML upload");
            try {
                pageFilterIndexService.indexPageFromXml(page);
            } catch (RuntimeException exception) {
                log.warn("Failed to index page {} after direct XML upload: {}", page.getId(), exception.getMessage());
            }

            return true;
        }
        return false;
    }

    public boolean uploadImages(String pageId, List<MultipartFile> images, List<String> variants, String userId) throws IOException {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent() && images != null && !images.isEmpty()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
            if (!workspaceAccessService.canManageProjects(workspaceId, userId)) {
                return false;
            }
            assertPageWritable(page);
            String projectId = page.getProject().getId();

            for (int i = 0; i < images.size(); i++) {
                MultipartFile image = images.get(i);
                if (!image.isEmpty() && isValidImageFile(image)) {
                    var storedFile = hierarchicalFileStorageService.storeMultipartFile(
                            image,
                            workspaceId,
                            projectId,
                            StoredFileType.IMG,
                            userId
                    );
                    String originalFileName = storedFile.originalFilename();

                    // Parse the image name to extract base name and variant
                    ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(originalFileName);

                    // Allow explicit variant override, otherwise use parsed variant
                    String variant = nameInfo.variant();
                    if (variants != null && i < variants.size() && !variants.get(i).isEmpty()) {
                        variant = variants.get(i);
                    }

                    PageImage pageImage = new PageImage(
                            originalFileName,
                            storedFile.storagePath(),
                            storedFile.mimeType(),
                            storedFile.sizeBytes(),
                            variant,
                            nameInfo.baseName(),
                            page
                    );

                    pageImageRepository.save(pageImage);

                    // Generate thumbnail asynchronously
                    try {
                        String thumbnailPath = thumbnailService.generateThumbnail(storedFile.storagePath());
                        if (thumbnailPath != null) {
                            pageImage.setThumbnailPath(thumbnailPath);
                            pageImageRepository.save(pageImage);
                        }
                    } catch (Exception e) {
                        // Log but don't fail the upload
                        log.warn("Failed to generate thumbnail: {}", e.getMessage());
                    }
                }
            }
            return true;
        }
        return false;
    }

    public List<PageImage> getPageImages(String pageId, String userId) {
        Optional<Page> pageOpt = getPageById(pageId, userId);
        if (pageOpt.isPresent()) {
            return pageImageRepository.findByPageId(pageId);
        }
        return List.of();
    }

    public List<PageXml> getPageXmlFiles(String pageId, String userId) {
        Optional<Page> pageOpt = getPageById(pageId, userId);
        if (pageOpt.isPresent()) {
            return pageXmlRepository.findByPage_Id(pageId).stream().toList();
        }
        return List.of();
    }

    public AnnotationDeleteResult deleteAnnotations(String projectId, List<String> pageIds, String userId) {
        List<String> normalizedIds = normalizeIds(pageIds);
        if (normalizedIds.isEmpty()) {
            return new AnnotationDeleteResult(0, 0, 0);
        }

        List<Page> pages = pageRepository.findAllByIdIn(normalizedIds).stream()
                .filter(page -> isPageAssetDeletable(page, projectId, userId))
                .toList();
        if (pages.isEmpty()) {
            return new AnnotationDeleteResult(0, 0, normalizedIds.size());
        }

        List<String> deletablePageIds = pages.stream().map(Page::getId).toList();
        List<PageXml> xmlFiles = pageXmlRepository.findByPage_IdIn(deletablePageIds);
        if (xmlFiles.isEmpty()) {
            return new AnnotationDeleteResult(0, 0, normalizedIds.size());
        }

        List<String> affectedPageIds = xmlFiles.stream()
                .map(PageXml::getPage)
                .filter(Objects::nonNull)
                .map(Page::getId)
                .distinct()
                .toList();
        List<String> xmlIds = xmlFiles.stream()
                .map(PageXml::getId)
                .filter(Objects::nonNull)
                .toList();
        List<String> storagePaths = xmlFiles.stream()
                .map(PageXml::getFilePath)
                .filter(Objects::nonNull)
                .toList();

        xmlIds.forEach(annotationReadCache::evict);
        pageXmlVersionService.deleteVersionDirectories(xmlIds);
        hierarchicalFileStorageService.deleteStoredFiles(storagePaths);
        pageTextContentRepository.deleteByPageIdIn(affectedPageIds);
        pageLabelIndexRepository.deleteByPageIdIn(affectedPageIds);
        pageConfidenceIndexRepository.deleteByPageIdIn(affectedPageIds);
        pageXmlAttributeIndexRepository.deleteByPageIdIn(affectedPageIds);
        pageXmlRepository.deleteAllInBatch(xmlFiles);

        pages.stream()
                .map(page -> page.getProject().getLibrary().getWorkspaceId())
                .filter(Objects::nonNull)
                .distinct()
                .forEach(workspaceQuotaRefreshService::scheduleUsageRefresh);

        log.info("Deleted {} annotation file(s) from {} page(s) in project {}",
                xmlFiles.size(), affectedPageIds.size(), projectId);
        return new AnnotationDeleteResult(affectedPageIds.size(), xmlFiles.size(), normalizedIds.size());
    }

    public ImageDeleteResult deleteImages(String projectId, String pageId, List<String> imageIds, String userId) {
        List<String> normalizedIds = normalizeIds(imageIds);
        if (normalizedIds.isEmpty()) {
            return new ImageDeleteResult(0, 0);
        }

        Optional<Page> pageOpt = pageRepository.findById(pageId);
        if (pageOpt.isEmpty() || !isPageAssetDeletable(pageOpt.get(), projectId, userId)) {
            return new ImageDeleteResult(0, normalizedIds.size());
        }

        List<PageImage> images = pageImageRepository.findAllById(normalizedIds).stream()
                .filter(image -> image.getPage() != null && pageId.equals(image.getPage().getId()))
                .toList();
        if (images.isEmpty()) {
            return new ImageDeleteResult(0, normalizedIds.size());
        }

        List<String> storagePaths = images.stream()
                .flatMap(image -> java.util.stream.Stream.of(image.getFilePath(), image.getThumbnailPath()))
                .filter(Objects::nonNull)
                .toList();
        hierarchicalFileStorageService.deleteStoredFiles(storagePaths);
        pageImageRepository.deleteAllInBatch(images);

        String workspaceId = pageOpt.get().getProject().getLibrary().getWorkspaceId();
        workspaceQuotaRefreshService.scheduleUsageRefresh(workspaceId);
        log.info("Deleted {} image(s) from page {} in project {}", images.size(), pageId, projectId);
        return new ImageDeleteResult(images.size(), normalizedIds.size());
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .toList();
    }

    private boolean isPageAssetDeletable(Page page, String projectId, String userId) {
        if (page == null || page.getProject() == null || !projectId.equals(page.getProject().getId())) {
            return false;
        }
        String workspaceId = page.getProject().getLibrary().getWorkspaceId();
        return workspaceAccessService.canManageProjects(workspaceId, userId)
                && !page.getProject().isLocked()
                && !page.isEffectivelyLocked();
    }

    public record AnnotationDeleteResult(
            int deletedPageCount,
            int deletedAnnotationCount,
            int requestedPageCount
    ) {}

    public record ImageDeleteResult(int deletedCount, int requestedCount) {}

    public Map<String, List<PageImage>> getPageImagesGroupedByBaseName(String pageId, String userId) {
        List<PageImage> images = getPageImages(pageId, userId);
        return images.stream().collect(Collectors.groupingBy(PageImage::getBaseName));
    }

    @Transactional
    public boolean transferImageToPage(String imageId, String targetPageId, String userId) {
        Optional<PageImage> imageOpt = pageImageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            return false;
        }

        PageImage image = imageOpt.get();

        // Check if user has access to the source page
        String sourcePageId = image.getPage().getId();
        if (getPageById(sourcePageId, userId).isEmpty()) {
            return false;
        }

        // Check if user has access to the target page
        Optional<Page> targetPageOpt = getPageById(targetPageId, userId);
        if (targetPageOpt.isEmpty()) {
            return false;
        }

        Page targetPage = targetPageOpt.get();
        assertPageWritable(image.getPage());
        assertPageWritable(targetPage);

        // Check if both pages are in the same workspace (for security)
        String sourceWorkspaceId = image.getPage().getProject().getLibrary().getWorkspaceId();
        String targetWorkspaceId = targetPage.getProject().getLibrary().getWorkspaceId();

        if (!sourceWorkspaceId.equals(targetWorkspaceId)) {
            return false;
        }

        // Transfer the image
        image.setPage(targetPage);
        pageImageRepository.save(image);

        return true;
    }

    private void notifyWorkspaceMembers(String workspaceId, String excludeUserId, String message, String pageId) {
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);

        for (WorkspaceMember member : members) {
            if (!member.getUserId().equals(excludeUserId) &&
                    member.getInvitationStatus() == WorkspaceMember.InvitationStatus.ACCEPTED) {

                notificationService.createPageCreatedNotification(
                        member.getUserId(),
                        message,
                        pageId
                );
            }
        }
    }

    private boolean isValidXmlFile(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();

        return (contentType != null && (contentType.equals("text/xml") || contentType.equals("application/xml"))) ||
                (fileName != null && fileName.toLowerCase().endsWith(".xml"));
    }

    private void assertPageWritable(Page page) {
        if (page == null || page.getProject() == null) {
            throw new IllegalStateException("Page is not writable");
        }
        if (page.getProject().isLocked()) {
            throw new IllegalStateException(page.getProject().getLockedReason() == null
                    ? "Project is locked"
                    : page.getProject().getLockedReason());
        }
        if (page.isEffectivelyLocked()) {
            throw new IllegalStateException(page.getEffectiveLockedReason() == null
                    ? "Page is locked"
                    : page.getEffectiveLockedReason());
        }
    }

    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private void deletePageFiles(Page page) {
        // Delete XML files (PageXml entities) and their version directories
        List<PageXml> xmlFiles = pageXmlRepository.findByPage_Id(page.getId()).stream().toList();
        List<PageImage> images = pageImageRepository.findByPageId(page.getId());
        deletePageFiles(List.of(page.getId()), xmlFiles, images);
    }

    private void deletePageFiles(List<String> pageIds, List<PageXml> xmlFiles, List<PageImage> images) {
        List<String> storagePaths = new ArrayList<>(xmlFiles.size() + (images.size() * 2));
        List<String> xmlIds = xmlFiles.stream()
                .map(PageXml::getId)
                .filter(Objects::nonNull)
                .toList();
        int deletedVersionDirectories = pageXmlVersionService.deleteVersionDirectories(xmlIds);

        for (PageXml xml : xmlFiles) {
            if (xml.getFilePath() != null) {
                storagePaths.add(xml.getFilePath());
            }
        }

        for (PageImage image : images) {
            if (image.getFilePath() != null) {
                storagePaths.add(image.getFilePath());
            }
            if (image.getThumbnailPath() != null) {
                storagePaths.add(image.getThumbnailPath());
            }
        }

        int deletedStoredFiles = hierarchicalFileStorageService.deleteStoredFiles(storagePaths);

        log.info("Deleted files for {} page(s): {} images, {} xml files",
                pageIds.size(), images.size(), xmlFiles.size());
        log.debug("Deleted {} XML version directory(ies) for page batch", deletedVersionDirectories);
        log.debug("Deleted {} stored file(s) for page batch", deletedStoredFiles);
    }

    public PageImage getImageById(String imageId, String userId) {
        Optional<PageImage> imageOpt = pageImageRepository.findById(imageId);
        if (imageOpt.isEmpty()) {
            return null;
        }

        PageImage image = imageOpt.get();

        // Verify user has access to this image through workspace membership
        Page page = image.getPage();
        if (page == null) {
            return null;
        }

        Project project = page.getProject();
        if (project == null) {
            return null;
        }
        String workspaceId = project.getLibrary().getWorkspaceId();
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId) ? image : null;
    }

    public PageXml getXmlById(String xmlId, String userId) {
        Optional<PageXml> xmlOpt = pageXmlRepository.findById(xmlId);
        if (xmlOpt.isEmpty()) {
            return null;
        }

        PageXml xml = xmlOpt.get();
        Page page = xml.getPage();
        if (page == null || page.getProject() == null) {
            return null;
        }

        String workspaceId = page.getProject().getLibrary().getWorkspaceId();
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId) ? xml : null;
    }
}
