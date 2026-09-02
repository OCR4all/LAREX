package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageXmlRawEditServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private PageService pageService;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private PageXmlVersionService pageXmlVersionService;
    @Mock
    private AnnotationReadCache annotationReadCache;
    @Mock
    private PageFilterIndexService pageFilterIndexService;
    @Mock
    private PageXmlValidationService pageXmlValidationService;
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    @Mock
    private AuthorizationPolicyService authorizationPolicyService;

    @Test
    void saveXmlText_blocksInvalidXmlWithoutPersistence() throws Exception {
        PageXmlRawEditService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path file = prepareXmlPath(pageXml.getFilePath(), "<old/>");

        when(pageService.getXmlById("xml-1", "user-1")).thenReturn(pageXml);
        when(authorizationPolicyService.canAccessWorkspace("ws-1", "user-1")).thenReturn(true);
        PageXmlTextDto.XmlValidationResult invalid = new PageXmlTextDto.XmlValidationResult(
                false,
                List.of(new PageXmlTextDto.XmlValidationError(3, 7, "error", "XSD_VALIDATION_ERROR", "invalid element")),
                "2019-07-15",
                "http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15"
        );
        when(pageXmlValidationService.validatePageXml("<new/>")).thenReturn(invalid);

        PageXmlTextDto.XmlValidationResult result = service.saveXmlText(
                "project-1",
                "page-1",
                "xml-1",
                "<new/>",
                null,
                "user-1"
        );

        assertFalse(result.valid());
        assertEquals("<old/>", Files.readString(file));
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(pageXmlRepository, never()).save(any());
        verify(annotationReadCache, never()).evict(any());
        verify(pageFilterIndexService, never()).indexPageFromXml(any());
    }

    @Test
    void saveXmlText_persistsWhenValidAndCreatesVersion() throws Exception {
        PageXmlRawEditService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path file = prepareXmlPath(pageXml.getFilePath(), "<old/>");

        when(pageService.getXmlById("xml-1", "user-1")).thenReturn(pageXml);
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        when(authorizationPolicyService.canAccessWorkspace("ws-1", "user-1")).thenReturn(true);

        PageXmlTextDto.XmlValidationResult valid = new PageXmlTextDto.XmlValidationResult(
                true,
                List.of(),
                "2013-07-15",
                "http://schema.primaresearch.org/PAGE/gts/pagecontent/2013-07-15"
        );
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2013-07-15">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000"/>
                </PcGts>
                """;
        when(pageXmlValidationService.validatePageXml(xml)).thenReturn(valid);

        PageXmlTextDto.XmlValidationResult result = service.saveXmlText(
                "project-1",
                "page-1",
                "xml-1",
                xml,
                null,
                "user-1"
        );

        assertTrue(result.valid());
        String savedXml = Files.readString(file);
        assertTrue(savedXml.contains("\n   <Metadata>\n      <Creator>tester</Creator>"));
        assertTrue(savedXml.endsWith("\n"));
        assertEquals("2013-07-15", pageXml.getSchemaVersion());
        verify(pageXmlVersionService).createVersion("xml-1", "user-1", "Saved from XML editor");
        verify(pageXmlRepository).save(pageXml);
        verify(annotationReadCache).evict("xml-1");
        verify(pageFilterIndexService).indexPageFromXml(eq(pageXml.getPage()));
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh("ws-1");
    }

    @Test
    void saveXmlText_blocksLockedProjects() throws Exception {
        PageXmlRawEditService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        pageXml.getPage().getProject().setLocked(true);
        prepareXmlPath(pageXml.getFilePath(), "<old/>");

        when(pageService.getXmlById("xml-1", "user-1")).thenReturn(pageXml);
        when(authorizationPolicyService.canAccessWorkspace("ws-1", "user-1")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> service.saveXmlText(
                "project-1",
                "page-1",
                "xml-1",
                "<new/>",
                null,
                "user-1"
        ));

        verify(pageXmlValidationService, never()).validatePageXml(anyString());
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(pageXmlRepository, never()).save(any());
    }

    private PageXmlRawEditService service() {
        UploadDirectoryProperties uploadDirectoryProperties = new UploadDirectoryProperties();
        uploadDirectoryProperties.setRootDirectory(tempDir);

        return new PageXmlRawEditService(
                pageService,
                pageXmlRepository,
                pageXmlVersionService,
                annotationReadCache,
                pageFilterIndexService,
                pageXmlValidationService,
                workspaceQuotaRefreshService,
                authorizationPolicyService,
                new UploadPathService(uploadDirectoryProperties)
        );
    }

    private Path prepareXmlPath(String relativePath, String content) throws Exception {
        Path path = tempDir.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private PageXml pageXml(String relativePath) {
        Project project = new Project();
        project.setId("project-1");
        Library library = new Library();
        library.setWorkspaceId("ws-1");
        project.setLibrary(library);

        Page page = new Page();
        page.setId("page-1");
        page.setProject(project);

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-1");
        pageXml.setFilePath(relativePath);
        pageXml.setSchema(XmlSchema.PAGE_XML);
        pageXml.setPage(page);
        return pageXml;
    }
}
