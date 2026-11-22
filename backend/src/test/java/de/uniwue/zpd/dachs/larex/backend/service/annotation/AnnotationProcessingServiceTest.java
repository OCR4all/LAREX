package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.MetadataItemDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.AnnotationAlreadyExistsException;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.LinkedDatasetItemSync;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.cache.AnnotationReadCache;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.events.AnnotationSavedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToOcrFormatExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.PageXmlWriteResult;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnotationProcessingServiceTest {

    private static final List<LabelsDto> TOOL_LABELS =
            List.of(new LabelsDto(null, null, null, null, List.of(new LabelDto("LAREX", "tool", null))));

    @TempDir
    Path tempDir;

    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private PageXmlToAnnotationParser pageXmlParser;
    @Mock
    private AnnotationToPageXmlExporter pageXmlExporter;
    @Mock
    private AnnotationToOcrFormatExporter altoXmlExporter;
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
    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private LinkedDatasetItemSync linkedDatasetItemSync;

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
    void parseXmlVersionToAnnotation_parsesVersionFileWithoutCache() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path versionPath = prepareXmlPath("xml/versions/xml-1/1.xml", "<PcGts/>");
        PageDto parsedDto = pageDto();

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(pageXmlVersionService.resolveVersionPath("version-1", "xml-1")).thenReturn(versionPath);
        when(pageXmlParser.parse(versionPath, pageXml)).thenReturn(parsedDto);

        PageDto actual = service.parseXmlVersionToAnnotation("xml-1", "version-1");

        assertSame(parsedDto, actual);
        verify(annotationReadCache, never()).getIfFresh(any(), any());
        verify(annotationReadCache, never()).put(any(), any(), any());
    }

    @Test
    void parseXmlVersionToAnnotation_rejectsNonPageXmlSchema() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        pageXml.setSchema(XmlSchema.ALTO_XML);

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));

        assertThrows(UnsupportedOperationException.class,
                () -> service.parseXmlVersionToAnnotation("xml-1", "version-1"));

        verify(pageXmlVersionService, never()).resolveVersionPath(any(), any());
        verify(pageXmlParser, never()).parse(any(), any());
    }

    @Test
    void saveAnnotationToXml_failsWhenVersionCreationFails() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        org.mockito.Mockito.doThrow(new IOException("versioning failed"))
                .when(pageXmlVersionService).createVersion("xml-1", "user-1", "Saved from annotation editor");

        assertThrows(IOException.class, () -> service.saveAnnotationToXml("xml-1", pageDto(), "user-1"));

        assertEquals("<PcGts/>", Files.readString(tempDir.resolve(pageXml.getFilePath())));
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveAnnotationToXml_rejectedWriteCreatesNoVersion() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path xmlPath = prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(pageXmlExporter.writeValidated(any(), eq(pageXml), any(Path.class)))
                .thenThrow(new IOException("PAGE XML validation failed before persistence"));

        assertThrows(IOException.class, () -> service.saveAnnotationToXml("xml-1", pageDto(), "user-1"));

        assertEquals("<PcGts/>", Files.readString(xmlPath));
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(annotationReadCache, never()).evict(any());
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveAnnotationToXml_writesValidatedXmlAndPublishesPostSaveEvent() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        Path xmlPath = prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");
        PageDto written = pageDto("as written");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(userService.getUserById("user-1")).thenReturn(Optional.of(new UserDto("user-1", "tester", null, null, null, null)));
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        doAnswer(invocation -> {
            Path tempPath = invocation.getArgument(2);
            Files.writeString(tempPath, "<PcGts><Page imageFilename=\"img.png\" imageWidth=\"1000\" imageHeight=\"1500\"/></PcGts>");
            return new PageXmlWriteResult(Files.size(tempPath), List.of(), "2024-07-15", written);
        }).when(pageXmlExporter).writeValidated(any(), eq(pageXml), any(Path.class));

        service.saveAnnotationToXml("xml-1", pageDto(), "user-1");

        assertEquals("<PcGts><Page imageFilename=\"img.png\" imageWidth=\"1000\" imageHeight=\"1500\"/></PcGts>",
                Files.readString(xmlPath));
        verify(annotationReadCache).evict("xml-1");
        verify(pageXmlVersionService).createVersion("xml-1", "user-1", "Saved from annotation editor");
        verify(annotationReadCache).put("xml-1", xmlPath, written);
        verify(pageXmlRepository).save(pageXml);
        ArgumentCaptor<AnnotationSavedEvent> event = ArgumentCaptor.forClass(AnnotationSavedEvent.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertSame(written, event.getValue().pageDto());
        verify(workspaceQuotaRefreshService).scheduleUsageRefresh("ws-1");
    }

    @Test
    void createInitialAnnotationXml_usesStoredImageDimensionsAndAuthenticatedCreator() throws Exception {
        AnnotationProcessingService service = service();
        Page page = pageXml("unused.xml").getPage();
        PageImage image = new PageImage(
                "img.png",
                "images/img.png",
                "image/png",
                1L,
                "original",
                "img",
                page
        );
        image.setId("image-1");
        page.setImages(Set.of(image));

        Path imagePath = tempDir.resolve(image.getFilePath());
        Files.createDirectories(imagePath.getParent());
        ImageIO.write(new BufferedImage(1200, 1800, BufferedImage.TYPE_BYTE_GRAY), "png", imagePath.toFile());

        when(pageRepository.findByIdAndProjectId("page-1", "project-1")).thenReturn(Optional.of(page));
        when(pageXmlRepository.findByPage_Id("page-1")).thenReturn(Optional.empty());
        when(userService.getUserById("user-1"))
                .thenReturn(Optional.of(new UserDto("user-1", "tester", null, "Test", "User", null)));
        when(pageXmlExporter.writeValidated(any(PageDto.class), isNull(), any(Path.class)))
                .thenReturn(new PageXmlWriteResult(10L, List.of(), "2024-07-15", pageDto()));
        when(hierarchicalFileStorageService.storeFromPath(
                any(Path.class),
                eq("Page 1.xml"),
                any(),
                eq("ws-1"),
                eq("project-1"),
                eq(StoredFileType.XML),
                eq("user-1"),
                eq(true)
        )).thenReturn(new HierarchicalFileStorageService.StoredFileDescriptor(
                "stored-1",
                "xml/page-1.xml",
                "Page 1.xml",
                "application/vnd.prima.page+xml",
                "xml",
                10L,
                "checksum",
                StoredFileType.XML
        ));
        when(pageXmlRepository.save(any(PageXml.class))).thenAnswer(invocation -> {
            PageXml saved = invocation.getArgument(0);
            saved.setId("xml-1");
            return saved;
        });

        service.createInitialAnnotationXml(
                "project-1",
                "page-1",
                pageDto("Umbra").withImageDimensions(200, 283),
                "user-1"
        );

        ArgumentCaptor<PageDto> writtenPage = ArgumentCaptor.forClass(PageDto.class);
        verify(pageXmlExporter).writeValidated(writtenPage.capture(), isNull(), any(Path.class));
        assertEquals(1200, writtenPage.getValue().imageWidth());
        assertEquals(1800, writtenPage.getValue().imageHeight());
        assertEquals("Test User (LAREX)", writtenPage.getValue().metadata().creator());
    }

    @Test
    void saveAnnotationToXml_keepsCreatorAndRecordsEditorsAsAuthorItems() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");
        MetadataItemDto otherEditor = new MetadataItemDto("author", "annotator", "Bob Builder", "2026-01-01T00:00:00", null);
        MetadataItemDto previousSave = new MetadataItemDto("author", "annotator", "Ada Lovelace", "2026-01-02T00:00:00", TOOL_LABELS);
        MetadataItemDto processingStep = new MetadataItemDto("processingStep", "ocr", "kraken", null, null);
        PageDto input = pageDto(new MetadataDto(
                "Transkribus", "2026-01-01T00:00:00", null, null, null, null,
                List.of(otherEditor, previousSave, processingStep)));

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(userService.getUserById("user-1"))
                .thenReturn(Optional.of(new UserDto("user-1", "ada", null, " Ada ", "Lovelace", null)));
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        when(pageXmlExporter.writeValidated(any(), eq(pageXml), any(Path.class)))
                .thenReturn(new PageXmlWriteResult(1L, List.of(), "2019-07-15", pageDto()));

        service.saveAnnotationToXml("xml-1", input, "user-1");

        ArgumentCaptor<PageDto> written = ArgumentCaptor.forClass(PageDto.class);
        verify(pageXmlExporter).writeValidated(written.capture(), eq(pageXml), any(Path.class));
        MetadataDto metadata = written.getValue().metadata();
        assertEquals("Transkribus", metadata.creator());
        assertEquals(3, metadata.items().size());
        assertSame(otherEditor, metadata.items().get(0));
        assertEquals("Ada Lovelace", metadata.items().get(1).value());
        assertEquals(metadata.lastChange(), metadata.items().get(1).date());
        assertEquals(TOOL_LABELS, metadata.items().get(1).labels());
        assertSame(processingStep, metadata.items().get(2));
    }

    @Test
    void saveAnnotationToXml_addsAuthorItemAndFallsBackToUsername() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(userService.getUserById("user-1"))
                .thenReturn(Optional.of(new UserDto("user-1", "tester", null, null, "  ", null)));
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        when(pageXmlExporter.writeValidated(any(), eq(pageXml), any(Path.class)))
                .thenReturn(new PageXmlWriteResult(1L, List.of(), "2019-07-15", pageDto()));

        service.saveAnnotationToXml("xml-1", pageDto(), "user-1");

        ArgumentCaptor<PageDto> written = ArgumentCaptor.forClass(PageDto.class);
        verify(pageXmlExporter).writeValidated(written.capture(), eq(pageXml), any(Path.class));
        MetadataDto metadata = written.getValue().metadata();
        assertEquals("tester (LAREX)", metadata.creator());
        assertTrue(metadata.lastChange().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"), metadata.lastChange());
        assertEquals(metadata.lastChange(), metadata.created());
        assertEquals(List.of(new MetadataItemDto("author", "annotator", "tester", metadata.lastChange(), TOOL_LABELS)),
                metadata.items());
    }

    @Test
    void saveAnnotationToXml_followsWorkspaceAttributionSettings() throws Exception {
        AnnotationProcessingService service = service();
        PageXml pageXml = pageXml("annotations/page.xml");
        prepareXmlPath(pageXml.getFilePath(), "<PcGts/>");
        TeamWorkspace workspace = new TeamWorkspace("Team", null, "owner");
        workspace.setRecordPageXmlAuthors(false);
        workspace.setPageXmlCreatorFromUser(false);
        MetadataItemDto existing = new MetadataItemDto("author", "annotator", "Bob Builder", "2026-01-01T00:00:00", null);

        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(pageXml));
        when(workspaceQueryService.findWorkspaceById("ws-1")).thenReturn(Optional.of(workspace));
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);
        when(pageXmlExporter.writeValidated(any(), eq(pageXml), any(Path.class)))
                .thenReturn(new PageXmlWriteResult(1L, List.of(), "2019-07-15", pageDto()));

        service.saveAnnotationToXml("xml-1", pageDto(new MetadataDto(null, null, null, null, null, null, List.of(existing))), "user-1");

        ArgumentCaptor<PageDto> written = ArgumentCaptor.forClass(PageDto.class);
        verify(pageXmlExporter).writeValidated(written.capture(), eq(pageXml), any(Path.class));
        assertEquals("LAREX", written.getValue().metadata().creator());
        assertEquals(List.of(existing), written.getValue().metadata().items());
        verify(userService, never()).getUserById(any());
    }

    @Test
    void createInitialAnnotationXml_doesNotOverwriteExistingPageXml() throws Exception {
        AnnotationProcessingService service = service();
        PageXml existing = pageXml("annotations/page.xml");

        when(pageRepository.findByIdAndProjectId("page-1", "project-1"))
                .thenReturn(Optional.of(existing.getPage()));
        when(pageXmlRepository.findByPage_Id("page-1")).thenReturn(Optional.of(existing));

        AnnotationAlreadyExistsException error = assertThrows(
                AnnotationAlreadyExistsException.class,
                () -> service.createInitialAnnotationXml("project-1", "page-1", pageDto(), "user-1")
        );

        assertEquals("xml-1", error.getXmlId());
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(pageXmlExporter, never()).writeValidated(any(), any(), any());
    }

    private AnnotationProcessingService service() {
        AnnotationProcessingService service = new AnnotationProcessingService(
                pageRepository,
                pageXmlRepository,
                pageXmlParser,
                pageXmlExporter,
                altoXmlExporter,
                pageXmlVersionService,
                hierarchicalFileStorageService,
                userService,
                annotationReadCache,
                applicationEventPublisher,
                workspaceQuotaRefreshService,
                workspaceQueryService,
                linkedDatasetItemSync
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
        return pageDto((MetadataDto) null);
    }

    private PageDto pageDto(String creator) {
        return pageDto(creator == null ? null : new MetadataDto(creator, null, null, null, null));
    }

    private PageDto pageDto(MetadataDto metadata) {
        return new PageDto(
                "img.png",
                1000,
                1500,
                null,
                null,
                null,
                metadata,
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
                null
        );
    }
}
