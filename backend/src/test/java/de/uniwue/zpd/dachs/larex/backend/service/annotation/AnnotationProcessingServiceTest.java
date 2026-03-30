package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.events.AnnotationSavedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToAltoXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.PageXmlWriteResult;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.AltoXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationProcessingServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private PageXmlToAnnotationParser pageXmlParser;
    @Mock
    private AltoXmlToAnnotationParser altoXmlParser;
    @Mock
    private AnnotationToPageXmlExporter pageXmlExporter;
    @Mock
    private AnnotationToAltoXmlExporter altoXmlExporter;
    @Mock
    private PageXmlVersionService pageXmlVersionService;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;
    @Mock
    private UserService userService;
    @Mock
    private AnnotationReadCache annotationReadCache;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    @Test
    void parseXmlToAnnotation_returnsCachedDtoWhenFingerprintMatches() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        PageDto cachedDto = pageDto();
        Path xmlPath = prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(annotationReadCache.getIfFresh("xml-1", xmlPath)).thenReturn(cachedDto);

        PageDto actual = service.parseXmlToAnnotation("xml-1");

        assertSame(cachedDto, actual);
        verify(pageXmlParser, never()).parse(any(), any());
    }

    @Test
    void saveAnnotationToXml_failsWhenVersionCreationFails() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        org.mockito.Mockito.doThrow(new IOException("versioning failed"))
                .when(pageXmlVersionService).createVersion("xml-1", "user-1", "Manual save");

        assertThrows(IOException.class, () -> service.saveAnnotationToXml("xml-1", pageDto(), "user-1"));

        verify(pageXmlExporter, never()).writeValidated(any(), any(), any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveAnnotationToXml_writesValidatedXmlAndPublishesPostSaveEvent() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path xmlPath = prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(userService.getUserById("user-1")).thenReturn(Optional.of(new UserDto("user-1", "tester", null, null, null, null)));
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        doAnswer(invocation -> {
            Path tempPath = invocation.getArgument(2);
            Files.writeString(tempPath, "<PcGts><Page imageFilename=\"img.png\" imageWidth=\"1000\" imageHeight=\"1500\"/></PcGts>");
            return new PageXmlWriteResult(Files.size(tempPath), List.of(), "2019-07-15");
        }).when(pageXmlExporter).writeValidated(any(), eq(pageXml), any(Path.class));

        service.saveAnnotationToXml("xml-1", pageDto(), "user-1");

        assertEquals("<PcGts><Page imageFilename=\"img.png\" imageWidth=\"1000\" imageHeight=\"1500\"/></PcGts>",
                Files.readString(xmlPath));
        verify(annotationReadCache).evict("xml-1");
        verify(annotationReadCache).put(eq("xml-1"), eq(xmlPath), any(PageDto.class));
        verify(pageXmlRepository).save(pageXml);
        verify(applicationEventPublisher).publishEvent(any(AnnotationSavedEvent.class));
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh("ws-1");
    }

    private AnnotationProcessingService service() {
        AnnotationProcessingService service = new AnnotationProcessingService(
                pageRepository,
                pageXmlRepository,
                pageXmlParser,
                altoXmlParser,
                pageXmlExporter,
                altoXmlExporter,
                pageXmlVersionService,
                hierarchicalFileStorageService,
                userService,
                annotationReadCache,
                applicationEventPublisher,
                workspaceQuotaRefreshService
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        return service;
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
        page.setName("Page 1");
        page.setProject(project);

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-1");
        pageXml.setFilePath(relativePath);
        pageXml.setSchema(XmlSchema.PAGE_XML);
        pageXml.setPage(page);
        return pageXml;
    }

    private PageDto pageDto() {
        return new PageDto(
                "img.png",
                1000,
                1500,
                null,
                null,
                null,
                null,
                "pcgts-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
