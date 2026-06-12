package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
    @Mock
    private PageXmlConversionService pageXmlConversionService;
    @Mock
    private PageXmlCanonicalizationService pageXmlCanonicalizationService;

    @Test
    void exportProjectPackage_embedsAuxiliaryOutputsUnderExportsDirectory() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ArchiveIoService archiveIoService = new ArchiveIoService(objectMapper);
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
                workspaceAccessService,
                archiveIoService,
                toolkitPackageService,
                hierarchicalFileStorageService,
                pageOrderService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        Path imagePath = tempDir.resolve("uploads/page.png");
        Path xmlPath = tempDir.resolve("uploads/page.xml");
        Files.createDirectories(imagePath.getParent());
        Files.writeString(imagePath, "img");
        Files.writeString(xmlPath, "<PcGts/>");

        Project project = project();
        Page page = page(project);
        page.setSortOrder(2000);
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId("project-1")).thenReturn(List.of(page));
        when(pageOrderService.projectOrderComparator())
                .thenReturn(Comparator.comparing(Page::getName, String.CASE_INSENSITIVE_ORDER));
        when(pageXmlVersionRepository.findByPageXml_IdOrderByVersionNumberDesc("xml-1")).thenReturn(List.of());
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.png")).thenReturn(imagePath);
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.xml")).thenReturn(xmlPath);
        when(pageXmlConversionService.normalizeTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(PageXmlConversionService.PRIMARY_PAGE_VERSION);
        when(pageXmlConversionService.isLegacyTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(false);
        doAnswer(invocation -> {
            invocation.<java.io.OutputStream>getArgument(2).write("<PcGts/>".getBytes());
            return null;
        }).when(pageXmlConversionService).writeFileToVersion(eq(xmlPath), eq(PageXmlConversionService.PRIMARY_PAGE_VERSION), any());
        when(toolkitPackageService.buildProjectToolkitSnapshot("ws-1", null, null, null, null, null, null))
                .thenReturn(new ToolkitPackageDto.ToolkitPackage(
                        new ToolkitPackageDto.PackageMeta("1.0", LocalDateTime.now(), "ws-1", "Workspace"),
                        List.of()
                ));
        Path embeddedPath = tempDir.resolve("embedded-project.txt");
        Files.writeString(embeddedPath, "embedded");
        when(documentExportService.exportEmbeddedProjectOutputs(eq(project), eq(List.of(page)), anyList()))
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
        boolean foundMets = false;
        boolean foundEmbedded = false;
        Integer exportedSortOrder = null;
        try (ZipInputStream zipIn = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    foundManifest = true;
                    ProjectPackageDto.PackageManifest manifest = objectMapper.readValue(zipIn.readAllBytes(), ProjectPackageDto.PackageManifest.class);
                    exportedSortOrder = manifest.pages().getFirst().sortOrder();
                }
                if ("mets.xml".equals(entry.getName())) {
                    foundMets = true;
                }
                if ("exports/project.txt".equals(entry.getName())) {
                    foundEmbedded = true;
                }
            }
        }

        assertTrue(foundManifest);
        assertTrue(foundMets);
        assertTrue(foundEmbedded);
        assertEquals(2000, exportedSortOrder);
    }

    @Test
    @SuppressWarnings("unchecked")
    void importPagesRestoresSortOrderWithIndexFallbackForLegacyPackages() {
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
                workspaceAccessService,
                new ArchiveIoService(new ObjectMapper().findAndRegisterModules()),
                toolkitPackageService,
                hierarchicalFileStorageService,
                pageOrderService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                new ObjectMapper().findAndRegisterModules()
        );
        when(pageRepository.save(any(Page.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ProjectPackageDto.PageSnapshot> snapshots = List.of(
                pageSnapshot("source-1", "Page 1", 5000),
                pageSnapshot("source-2", "Page 2", null)
        );

        Map<String, Page> imported = (Map<String, Page>) ReflectionTestUtils.invokeMethod(
                service,
                "importPages",
                project(),
                snapshots
        );

        assertEquals(5000, imported.get("source-1").getSortOrder());
        assertEquals(PageOrderService.SORT_ORDER_STEP, imported.get("source-2").getSortOrder());
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
        page.setXmlFiles(new HashSet<>(Set.of(xml)));
        return page;
    }

    private ProjectPackageDto.PageSnapshot pageSnapshot(String sourcePageId, String name, Integer sortOrder) {
        return new ProjectPackageDto.PageSnapshot(
                sourcePageId,
                name,
                "desc",
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                false,
                null,
                sortOrder
        );
    }
}
