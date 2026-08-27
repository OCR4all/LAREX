package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.github.benmanes.caffeine.cache.Cache;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import de.uniwue.zpd.dachs.larex.backend.config.ProjectPackageProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ToolkitPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectPackageReleaseRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.page.PageOrderService;
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.toolkit.ToolkitPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectPackageServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectPackageReleaseRepository projectPackageReleaseRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private PageRepository pageRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private PageXmlVersionRepository pageXmlVersionRepository;
    @Mock
    private CodecRepository codecRepository;
    @Mock
    private ControlledDictionaryRepository dictionaryRepository;
    @Mock
    private LabelSetRepository labelSetRepository;
    @Mock
    private TagSetRepository tagSetRepository;
    @Mock
    private NormalizationProfileRepository normalizationProfileRepository;
    @Mock
    private ValidationRulesetRepository validationRulesetRepository;
    @Mock
    private VirtualKeyboardRepository virtualKeyboardRepository;
    @Mock
    private WorkspaceAccessService workspaceAccessService;
    @Mock
    private ToolkitPackageService toolkitPackageService;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;
    @Mock
    private PageOrderService pageOrderService;
    @Mock
    private PageFilterIndexService pageFilterIndexService;
    @Mock
    private StorageTrackingService storageTrackingService;
    @Mock
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;
    @Mock
    private DocumentExportService documentExportService;
    @Mock
    private PageXmlVersionService pageXmlVersionService;

    private final Map<String, PageXml> xmlHeadsByPageId = new java.util.LinkedHashMap<>();

    @BeforeEach
    void setUpXmlHeadQueries() {
        lenient().when(pageXmlRepository.findByPage_IdIn(anyList()))
                .thenAnswer(invocation -> invocation.<List<String>>getArgument(0).stream()
                        .map(xmlHeadsByPageId::get)
                        .filter(java.util.Objects::nonNull)
                        .toList());
    }
    @Mock
    private PageXmlConversionService pageXmlConversionService;
    @Mock
    private PageXmlCanonicalizationService pageXmlCanonicalizationService;

    @Test
    void exportProjectPackage_embedsAuxiliaryOutputsUnderExportsDirectory() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ArchiveIoService archiveIoService = new ArchiveIoService(objectMapper);
        ProjectPackageArchiveService projectPackageArchiveService =
                new ProjectPackageArchiveService(
                        archiveIoService,
                        objectMapper,
                        new ProjectPackageProperties()
                );
        ProjectPackageService service = new ProjectPackageService(
                projectRepository,
                projectPackageReleaseRepository,
                libraryRepository,
                pageRepository,
                pageXmlRepository,
                pageXmlVersionRepository,
                codecRepository,
                dictionaryRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                virtualKeyboardRepository,
                workspaceAccessService,
                archiveIoService,
                projectPackageArchiveService,
                toolkitPackageService,
                hierarchicalFileStorageService,
                pageOrderService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                objectMapper,
                new ProjectPackageProperties()
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        Path imagePath = tempDir.resolve("uploads/page.png");
        Path xmlPath = tempDir.resolve("uploads/page.xml");
        Files.createDirectories(imagePath.getParent());
        Files.writeString(imagePath, "img");
        Files.writeString(xmlPath, "<PcGts/>");

        Project project = project();
        Page page = page(project);
        PageImage missingImage = new PageImage();
        missingImage.setId("img-missing");
        missingImage.setFileName("missing.png");
        missingImage.setFilePath("uploads/missing.png");
        missingImage.setMimeType("image/png");
        missingImage.setVariant("missing");
        missingImage.setBaseName("missing");
        missingImage.setPage(page);
        page.getImages().add(missingImage);
        page.setSortOrder(2000);
        Page collidingPage = page(project);
        collidingPage.setId("page-2");
        collidingPage.setName("Page/");
        collidingPage.setSortOrder(3000);
        project.setPages(new ArrayList<>(List.of(page, collidingPage)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId("project-1")).thenReturn(List.of(page, collidingPage));
        when(pageOrderService.projectOrderComparator())
                .thenReturn(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER));
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.png")).thenReturn(imagePath);
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/missing.png"))
                .thenReturn(tempDir.resolve("uploads/missing.png"));
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.xml")).thenReturn(xmlPath);
        when(pageXmlConversionService.normalizeTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(PageXmlConversionService.PRIMARY_PAGE_VERSION);
        when(pageXmlConversionService.isLegacyTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(false);
        doAnswer(invocation -> {
            invocation.<java.io.OutputStream>getArgument(2).write("<PcGts/>".getBytes());
            return null;
        }).when(pageXmlConversionService).writeFileToVersion(eq(xmlPath), eq(PageXmlConversionService.PRIMARY_PAGE_VERSION), any());
        when(toolkitPackageService.buildProjectToolkitSnapshot("ws-1", null, null, null, null, null, null, null))
                .thenReturn(new ToolkitPackageDto.ToolkitPackage(
                        new ToolkitPackageDto.PackageMeta("1.0", LocalDateTime.now(), "ws-1", "Workspace"),
                        List.of()
                ));
        Path embeddedPath = tempDir.resolve("embedded-project.txt");
        Files.writeString(embeddedPath, "embedded");
        when(documentExportService.exportEmbeddedProjectOutputs(eq(project), eq(List.of(page, collidingPage)), anyList()))
                .thenReturn(List.of(new DocumentExportService.EmbeddedProjectOutput("exports/project.txt", embeddedPath, Files.size(embeddedPath))));

        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        service.writeProjectPackageInternal(
                "ws-1",
                "project-1",
                new ProjectPackageDto.ExportRequest(
                        null,
                        PageXmlConversionService.PRIMARY_PAGE_VERSION,
                        List.of(new DocumentExportDto.EmbeddedProjectOutputRequest(
                                DocumentExportDto.ExportFormat.TXT,
                                true,
                                DocumentExportDto.TextLevel.TEXT_LINE,
                                1,
                                null,
                                null,
                                null,
                                null
                        ))
                ),
                zipOut
        );
        byte[] zipBytes = zipOut.toByteArray();

        boolean foundManifest = false;
        boolean foundEmbedded = false;
        boolean foundPageDescriptor = false;
        boolean foundReadableImage = false;
        boolean foundReadableXml = false;
        List<String> pageDescriptorPaths = List.of();
        List<String> manifestWarnings = List.of();
        ProjectPackageDto.PageDescriptor exportedPage = null;
        try (ZipInputStream zipIn = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    foundManifest = true;
                    ProjectPackageDto.PackageManifest manifest = objectMapper.readValue(zipIn.readAllBytes(), ProjectPackageDto.PackageManifest.class);
                    assertEquals(ProjectPackageDto.DEFAULT_SCHEMA_VERSION, manifest.schemaVersion());
                    assertFalse(manifest.includesXmlHistory());
                    pageDescriptorPaths = manifest.pages();
                    manifestWarnings = manifest.warnings();
                }
                if ("exports/project.txt".equals(entry.getName())) {
                    foundEmbedded = true;
                }
                if (entry.getName().endsWith("/page.json")) {
                    foundPageDescriptor = true;
                    exportedPage = objectMapper.readValue(zipIn.readAllBytes(), ProjectPackageDto.PageDescriptor.class);
                }
                if (entry.getName().endsWith("/images/page.png")) {
                    foundReadableImage = true;
                }
                if (entry.getName().endsWith("/xml/page.xml")) {
                    foundReadableXml = true;
                }
            }
        }

        assertTrue(foundManifest);
        assertEquals(
                List.of("pages/Page/page.json", "pages/Page-2/page.json"),
                pageDescriptorPaths
        );
        assertTrue(foundPageDescriptor);
        assertTrue(foundReadableImage);
        assertTrue(foundReadableXml);
        assertTrue(foundEmbedded);
        assertTrue(manifestWarnings.stream().anyMatch(warning -> warning.contains("missing.png")));
        assertEquals(1, exportedPage.images().size());
        assertEquals("images/page.png", exportedPage.images().getFirst().path());
        assertEquals("xml/page.xml", exportedPage.xml().getFirst().path());
        assertTrue(exportedPage.xml().getFirst().history().isEmpty());
    }

    @Test
    void exportBasicProject_writesFlatArchiveWithOriginalFilenames() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ArchiveIoService archiveIoService = new ArchiveIoService(objectMapper);
        ProjectPackageArchiveService projectPackageArchiveService =
                new ProjectPackageArchiveService(
                        archiveIoService,
                        objectMapper,
                        new ProjectPackageProperties()
                );
        ProjectPackageService service = new ProjectPackageService(
                projectRepository,
                projectPackageReleaseRepository,
                libraryRepository,
                pageRepository,
                pageXmlRepository,
                pageXmlVersionRepository,
                codecRepository,
                dictionaryRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                virtualKeyboardRepository,
                workspaceAccessService,
                archiveIoService,
                projectPackageArchiveService,
                toolkitPackageService,
                hierarchicalFileStorageService,
                pageOrderService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                objectMapper,
                new ProjectPackageProperties()
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        Path imagePath = tempDir.resolve("uploads/page.png");
        Path secondImagePath = tempDir.resolve("uploads/page-copy.png");
        Path xmlPath = tempDir.resolve("uploads/page.xml");
        Files.createDirectories(imagePath.getParent());
        Files.writeString(imagePath, "img");
        Files.writeString(secondImagePath, "img2");
        Files.writeString(xmlPath, "<PcGts/>");

        Project project = project();
        Page page = page(project);
        PageImage secondImage = new PageImage();
        secondImage.setId("img-2");
        secondImage.setFileName("page.png");
        secondImage.setFilePath("uploads/page-copy.png");
        secondImage.setMimeType("image/png");
        secondImage.setVariant("secondary");
        secondImage.setBaseName("page");
        secondImage.setPage(page);
        page.getImages().add(secondImage);
        PageImage missingImage = new PageImage();
        missingImage.setId("img-3");
        missingImage.setFileName("missing.png");
        missingImage.setFilePath("uploads/missing.png");
        missingImage.setMimeType("image/png");
        missingImage.setVariant("tertiary");
        missingImage.setBaseName("missing");
        missingImage.setPage(page);
        page.getImages().add(missingImage);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId("project-1")).thenReturn(List.of(page));
        when(pageOrderService.projectOrderComparator())
                .thenReturn(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER));
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.png")).thenReturn(imagePath);
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page-copy.png")).thenReturn(secondImagePath);
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/missing.png"))
                .thenReturn(tempDir.resolve("uploads/missing.png"));
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.xml")).thenReturn(xmlPath);
        when(pageXmlConversionService.normalizeTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(PageXmlConversionService.PRIMARY_PAGE_VERSION);
        doAnswer(invocation -> {
            invocation.<java.io.OutputStream>getArgument(2).write("<PcGts/>".getBytes());
            return null;
        }).when(pageXmlConversionService).writeFileToVersion(any(Path.class), eq(PageXmlConversionService.PRIMARY_PAGE_VERSION), any());
        Path embeddedPath = tempDir.resolve("project.txt");
        Files.writeString(embeddedPath, "embedded");
        when(documentExportService.exportEmbeddedProjectOutputs(eq(project), eq(List.of(page)), anyList()))
                .thenReturn(List.of(new DocumentExportService.EmbeddedProjectOutput("exports/project.txt", embeddedPath, Files.size(embeddedPath))));

        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        service.writeBasicProjectExportInternal(
                "ws-1",
                "project-1",
                new ProjectPackageDto.ExportRequest(
                        null,
                        PageXmlConversionService.PRIMARY_PAGE_VERSION,
                        List.of(new DocumentExportDto.EmbeddedProjectOutputRequest(
                                DocumentExportDto.ExportFormat.TXT,
                                true,
                                DocumentExportDto.TextLevel.TEXT_LINE,
                                1,
                                null,
                                null,
                                null,
                                null
                        ))
                ),
                zipOut
        );

        Set<String> entries = new HashSet<>();
        String exportWarnings = null;
        try (ZipInputStream zipIn = new ZipInputStream(new java.io.ByteArrayInputStream(zipOut.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                entries.add(entry.getName());
                if ("_export-warnings.txt".equals(entry.getName())) {
                    exportWarnings = new String(zipIn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }

        assertTrue(entries.contains("page.png"));
        assertTrue(entries.contains("page (1).png"));
        assertTrue(entries.contains("page.xml"));
        assertTrue(entries.contains("project.txt"));
        assertTrue(entries.contains("_export-warnings.txt"));
        assertTrue(exportWarnings.contains("missing.png"));
        assertFalse(entries.contains("manifest.json"));
        assertFalse(entries.contains("mets.xml"));
        assertFalse(entries.stream().anyMatch(name -> name.contains("/") || name.contains("img-1") || name.contains("xml-1")));
    }

    @Test
    void previewCacheUsesConfiguredByteAndSessionBudgets() {
        ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        ArchiveIoService archiveIoService = new ArchiveIoService(objectMapper);
        ProjectPackageProperties properties = new ProjectPackageProperties();
        properties.getPreview().setMaxCachedBytes(8 * 1024L);
        properties.getPreview().setMaxSessions(2);
        ProjectPackageArchiveService projectPackageArchiveService =
                new ProjectPackageArchiveService(archiveIoService, objectMapper, properties);
        ProjectPackageService service = new ProjectPackageService(
                projectRepository,
                projectPackageReleaseRepository,
                libraryRepository,
                pageRepository,
                pageXmlRepository,
                pageXmlVersionRepository,
                codecRepository,
                dictionaryRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                virtualKeyboardRepository,
                workspaceAccessService,
                archiveIoService,
                projectPackageArchiveService,
                toolkitPackageService,
                hierarchicalFileStorageService,
                pageOrderService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                objectMapper,
                properties
        );

        assertEquals(8 * 1024L, ReflectionTestUtils.getField(service, "maxPreviewCacheBytes"));
        assertEquals(8L, ReflectionTestUtils.getField(service, "maxPreviewCacheWeight"));
        assertEquals(4, ReflectionTestUtils.getField(service, "minimumPreviewCacheWeight"));
        assertEquals(4, (int) ReflectionTestUtils.invokeMethod(service, "previewCacheWeight", 1L));
        assertEquals(5, (int) ReflectionTestUtils.invokeMethod(service, "previewCacheWeight", 5 * 1024L));

        Cache<?, ?> cache = (Cache<?, ?>) ReflectionTestUtils.getField(service, "importPreviewCache");
        assertEquals(8L, cache.policy().eviction().orElseThrow().getMaximum());
    }

    private Project project() {
        Library library = new Library("ws-1", "Library");
        library.setId("lib-1");

        Project project = new Project("Project", "desc", library);
        project.setId("project-1");
        project.setTags(List.of());
        return project;
    }

    private Page page(Project project) {
        Page page = new Page("Page", "desc", project);
        page.setId("page-1");
        page.setTags(List.of());

        PageImage image = new PageImage();
        image.setId("img-1");
        image.setFileName("page.png");
        image.setFilePath("uploads/page.png");
        image.setMimeType("image/png");
        image.setVariant("main");
        image.setBaseName("page");
        image.setPage(page);

        PageXml xml = new PageXml();
        xml.setId("xml-1");
        xml.setFileName("page.xml");
        xml.setFilePath("uploads/page.xml");
        xml.setMimeType("application/xml");
        xml.setVariant("main");
        xml.setBaseName("page");
        xml.setSchema(XmlSchema.PAGE_XML);
        xml.setSchemaVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION);
        xml.setPage(page);

        page.setImages(new HashSet<>(Set.of(image)));
        xmlHeadsByPageId.put(page.getId(), xml);
        return page;
    }

}
