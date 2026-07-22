package de.uniwue.zpd.dachs.larex.backend.service.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageConfidenceIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageLabelIndexRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.task.TaskPageLinkRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.ThumbnailService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PageServiceAssetDeletionTest {

    @Mock PageRepository pageRepository;
    @Mock PageImageRepository pageImageRepository;
    @Mock PageXmlRepository pageXmlRepository;
    @Mock PageTextContentRepository pageTextContentRepository;
    @Mock PageLabelIndexRepository pageLabelIndexRepository;
    @Mock PageConfidenceIndexRepository pageConfidenceIndexRepository;
    @Mock TaskPageLinkRepository taskPageLinkRepository;
    @Mock ProjectRepository projectRepository;
    @Mock WorkspaceMemberRepository workspaceMemberRepository;
    @Mock NotificationService notificationService;
    @Mock WorkspaceAccessService workspaceAccessService;
    @Mock ThumbnailService thumbnailService;
    @Mock PageXmlVersionService pageXmlVersionService;
    @Mock HierarchicalFileStorageService hierarchicalFileStorageService;
    @Mock PageXmlCanonicalizationService pageXmlCanonicalizationService;
    @Mock WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    @Mock PageOrderService pageOrderService;
    @Mock AnnotationReadCache annotationReadCache;

    private PageService service;

    @BeforeEach
    void setUp() {
        service = new PageService(
                pageRepository,
                pageImageRepository,
                pageXmlRepository,
                pageTextContentRepository,
                pageLabelIndexRepository,
                pageConfidenceIndexRepository,
                taskPageLinkRepository,
                projectRepository,
                workspaceMemberRepository,
                notificationService,
                workspaceAccessService,
                thumbnailService,
                pageXmlVersionService,
                hierarchicalFileStorageService,
                pageXmlCanonicalizationService,
                workspaceQuotaRefreshService,
                pageOrderService,
                annotationReadCache
        );
    }

    @Test
    void deleteAnnotationsRemovesFilesVersionsIndexesAndCache() {
        Page page = page("page-1");
        PageXml xml = new PageXml(
                "page.xml", "xml/page.xml", "application/xml", 42L,
                "original", "page", XmlSchema.PAGE_XML, null, page
        );
        xml.setId("xml-1");

        when(pageRepository.findAllByIdIn(List.of("page-1"))).thenReturn(List.of(page));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);
        when(pageXmlRepository.findByPage_IdIn(List.of("page-1"))).thenReturn(List.of(xml));

        PageService.AnnotationDeleteResult result = service.deleteAnnotations(
                "project-1", List.of("page-1"), "user-1"
        );

        assertEquals(1, result.deletedPageCount());
        assertEquals(1, result.deletedAnnotationCount());
        assertEquals(1, result.requestedPageCount());
        verify(annotationReadCache).evict("xml-1");
        verify(pageXmlVersionService).deleteVersionDirectories(List.of("xml-1"));
        verify(hierarchicalFileStorageService).deleteStoredFiles(List.of("xml/page.xml"));
        verify(pageTextContentRepository).deleteByPageIdIn(List.of("page-1"));
        verify(pageLabelIndexRepository).deleteByPageIdIn(List.of("page-1"));
        verify(pageConfidenceIndexRepository).deleteByPageIdIn(List.of("page-1"));
        verify(pageXmlRepository).deleteAllInBatch(List.of(xml));
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh("workspace-1");
    }

    @Test
    void deleteImagesOnlyDeletesImagesBelongingToRequestedPage() {
        Page page = page("page-1");
        Page otherPage = page("page-2");
        PageImage image = image("image-1", "img/page.png", "thumb/page.png", page);
        PageImage otherImage = image("image-2", "img/other.png", "thumb/other.png", otherPage);

        when(pageRepository.findById("page-1")).thenReturn(Optional.of(page));
        when(workspaceAccessService.canManageProjects("workspace-1", "user-1")).thenReturn(true);
        when(pageImageRepository.findAllById(List.of("image-1", "image-2")))
                .thenReturn(List.of(image, otherImage));

        PageService.ImageDeleteResult result = service.deleteImages(
                "project-1", "page-1", List.of("image-1", "image-2"), "user-1"
        );

        assertEquals(1, result.deletedCount());
        assertEquals(2, result.requestedCount());
        verify(hierarchicalFileStorageService)
                .deleteStoredFiles(List.of("img/page.png", "thumb/page.png"));
        verify(pageImageRepository).deleteAllInBatch(List.of(image));
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh("workspace-1");
        verify(pageImageRepository, never()).deleteAllInBatch(List.of(otherImage));
    }

    private Page page(String pageId) {
        Library library = new Library("workspace-1", "Library");
        Project project = new Project("Project", null, library);
        project.setId("project-1");
        Page page = new Page(pageId, null, project);
        page.setId(pageId);
        return page;
    }

    private PageImage image(String id, String filePath, String thumbnailPath, Page page) {
        PageImage image = new PageImage(
                id + ".png", filePath, "image/png", 42L, "original", id, page
        );
        image.setId(id);
        image.setThumbnailPath(thumbnailPath);
        return image;
    }
}
