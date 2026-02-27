package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceMember;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import de.uniwue.zpd.dachs.larex.backend.repository.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.PageXmlVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class PageService {

    private static final Logger log = LoggerFactory.getLogger(PageService.class);

    private final PageRepository pageRepository;
    private final PageImageRepository pageImageRepository;
    private final PageXmlRepository pageXmlRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final NotificationService notificationService;
    private final WorkspaceAccessService workspaceAccessService;
    private final ThumbnailService thumbnailService;
    private final PageXmlVersionService pageXmlVersionService;
    private final HierarchicalFileStorageService hierarchicalFileStorageService;

    public PageService(
            PageRepository pageRepository,
            PageImageRepository pageImageRepository,
            PageXmlRepository pageXmlRepository,
            ProjectRepository projectRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            NotificationService notificationService,
            WorkspaceAccessService workspaceAccessService,
            ThumbnailService thumbnailService,
            PageXmlVersionService pageXmlVersionService,
            HierarchicalFileStorageService hierarchicalFileStorageService) {

        this.pageRepository = pageRepository;
        this.pageImageRepository = pageImageRepository;
        this.pageXmlRepository = pageXmlRepository;
        this.projectRepository = projectRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.notificationService = notificationService;
        this.workspaceAccessService = workspaceAccessService;
        this.thumbnailService = thumbnailService;
        this.pageXmlVersionService = pageXmlVersionService;
        this.hierarchicalFileStorageService = hierarchicalFileStorageService;
    }

    public List<Page> getProjectPages(String projectId, String userId) {
        Optional<Project> projectOpt = projectRepository.findById(projectId);
        if (projectOpt.isPresent()) {
            String workspaceId = projectOpt.get().getLibrary().getWorkspaceId();
            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
                return pageRepository.findByProjectId(projectId);
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

            if (workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)) {
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

                return true;
            }
        }
        return false;
    }

    public int deletePages(List<String> pageIds, String userId) {
        List<String> deletedPageNames = new ArrayList<>();
        String projectId = null;
        String projectName = null;

        for (String pageId : pageIds) {
            Optional<Page> pageOpt = getPageById(pageId, userId);
            if (pageOpt.isPresent()) {
                Page page = pageOpt.get();
                if (projectId == null) {
                    projectId = page.getProject().getId();
                    projectName = page.getProject().getName();
                }
                String pageName = page.getName();
                if (deletePage(pageId, userId, false)) {
                    deletedPageNames.add(pageName);
                }
            }
        }

        if (!deletedPageNames.isEmpty() && projectId != null) {
            notificationService.createBatchPageDeletedNotification(userId, deletedPageNames, projectId, projectName);
        }

        return deletedPageNames.size();
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
                return pageRepository.findByProjectIdAndTagsIn(projectId, tags);
            }
        }
        return List.of();
    }

    public boolean uploadXmlFile(String pageId, MultipartFile xmlFile, String userId) throws IOException {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent() && xmlFile != null && !xmlFile.isEmpty()) {
            Page page = pageOpt.get();

            if (!isValidXmlFile(xmlFile)) {
                return false;
            }

            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
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
            pageXmlRepository.save(pageXml);

            return true;
        }
        return false;
    }

    public boolean uploadImages(String pageId, List<MultipartFile> images, List<String> variants, String userId) throws IOException {
        Optional<Page> pageOpt = getPageById(pageId, userId);

        if (pageOpt.isPresent() && images != null && !images.isEmpty()) {
            Page page = pageOpt.get();
            String workspaceId = page.getProject().getLibrary().getWorkspaceId();
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
            return pageXmlRepository.findByPage_Id(pageId);
        }
        return List.of();
    }

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

    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image/");
    }

    private void deletePageFiles(Page page) {
        // Delete XML files (PageXml entities) and their version directories
        List<PageXml> xmlFiles = pageXmlRepository.findByPage_Id(page.getId());
        for (PageXml xml : xmlFiles) {
            // Delete version directory first
            pageXmlVersionService.deleteVersionDirectory(xml.getId());
            hierarchicalFileStorageService.deleteStoredFile(xml.getFilePath());
        }

        // Delete images and their thumbnails
        List<PageImage> images = pageImageRepository.findByPageId(page.getId());
        for (PageImage image : images) {
            hierarchicalFileStorageService.deleteStoredFile(image.getFilePath());

            // Delete the thumbnail if it exists
            if (image.getThumbnailPath() != null) {
                hierarchicalFileStorageService.deleteStoredFile(image.getThumbnailPath());
            }
        }

        log.info("Deleted files for page {}: {} images, {} xml files",
                page.getId(), images.size(), xmlFiles.size());
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
