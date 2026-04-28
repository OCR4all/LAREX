package de.uniwue.zpd.dachs.larex.backend.service.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.UtilityPackageDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile.StoredFileType;
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
import de.uniwue.zpd.dachs.larex.backend.service.page.indexing.PageFilterIndexService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService.StoredFileDescriptor;
import de.uniwue.zpd.dachs.larex.backend.service.storage.StorageTrackingService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.utility.UtilityPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlCanonicalizationService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
    private UtilityPackageService utilityPackageService;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;
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
                utilityPackageService,
                hierarchicalFileStorageService,
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
        project.setPages(new ArrayList<>(List.of(page)));

        when(projectRepository.findWithAssociationsById("project-1")).thenReturn(Optional.of(project));
        when(pageRepository.findByProjectId("project-1")).thenReturn(List.of(page));
        when(pageXmlVersionRepository.findByPageXml_IdOrderByVersionNumberDesc("xml-1")).thenReturn(List.of());
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.png")).thenReturn(imagePath);
        when(hierarchicalFileStorageService.resolveUploadPath("uploads/page.xml")).thenReturn(xmlPath);
        when(pageXmlConversionService.normalizeTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(PageXmlConversionService.PRIMARY_PAGE_VERSION);
        when(pageXmlConversionService.isLegacyTargetVersion(PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn(false);
        when(pageXmlConversionService.convertFileToVersion(xmlPath, PageXmlConversionService.PRIMARY_PAGE_VERSION))
                .thenReturn("<PcGts/>".getBytes());
        when(utilityPackageService.buildProjectUtilitySnapshot("ws-1", null, null, null, null, null, null))
                .thenReturn(new UtilityPackageDto.UtilityPackage(
                        new UtilityPackageDto.PackageMeta("1.0", LocalDateTime.now(), "ws-1", "Workspace"),
                        List.of()
                ));
        when(documentExportService.exportEmbeddedProjectOutputs(eq(project), eq(List.of(page)), anyList()))
                .thenReturn(List.of(new DocumentExportService.EmbeddedProjectOutput("exports/project.txt", "embedded".getBytes())));

        byte[] zipBytes = service.exportProjectPackageInternal(
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
                )
        );

        boolean foundManifest = false;
        boolean foundMets = false;
        boolean foundEmbedded = false;
        try (ZipInputStream zipIn = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                if ("manifest.json".equals(entry.getName())) {
                    foundManifest = true;
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
    }

    @Test
    void importLegacyOcr4allProject_createsProjectFromInputAndProcessingDirectories() throws Exception {
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
                utilityPackageService,
                hierarchicalFileStorageService,
                pageFilterIndexService,
                storageTrackingService,
                workspaceQuotaGuardService,
                pageXmlConversionService,
                pageXmlCanonicalizationService,
                documentExportService,
                objectMapper
        );
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        Library library = new Library("ws-1", "Library");
        library.setId("lib-1");

        MockMultipartFile originalImage = new MockMultipartFile(
                "files",
                "legacy/input/0001.png",
                "image/png",
                "original".getBytes()
        );
        MockMultipartFile processingImage = new MockMultipartFile(
                "files",
                "legacy/processing/bin/0001.png",
                "image/png",
                "variant".getBytes()
        );
        MockMultipartFile pageXml = new MockMultipartFile(
                "files",
                "legacy/processing/0001.xml",
                "application/xml",
                "<PcGts/>".getBytes()
        );

        when(libraryRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(library));
        when(projectRepository.existsByNameAndLibraryId("legacy", "lib-1")).thenReturn(false);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId("project-legacy");
            return project;
        });
        when(pageRepository.save(any(Page.class))).thenAnswer(invocation -> {
            Page page = invocation.getArgument(0);
            page.setId("page-" + page.getName());
            return page;
        });
        when(pageXmlRepository.save(any(PageXml.class))).thenAnswer(invocation -> {
            PageXml xml = invocation.getArgument(0);
            xml.setId("xml-1");
            return xml;
        });
        when(workspaceQuotaGuardService.reserveBytesOrThrow(eq("ws-1"), anyLong(), eq("legacy-ocr4all-import")))
                .thenReturn(20L);
        when(hierarchicalFileStorageService.storeMultipartFile(
                any(MultipartFile.class),
                eq("ws-1"),
                eq("project-legacy"),
                any(StoredFileType.class),
                eq("user-1")
        )).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            StoredFileType fileType = invocation.getArgument(3);
            String fileName = Path.of(file.getOriginalFilename()).getFileName().toString();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
            String mimeType = fileType == StoredFileType.XML ? "application/xml" : file.getContentType();
            return new StoredFileDescriptor(
                    fileName + "-uuid",
                    "stored/" + fileName,
                    fileName,
                    mimeType,
                    extension,
                    file.getSize(),
                    "sha",
                    fileType
            );
        });

        ProjectPackageDto.ImportResult result = service.importLegacyOcr4allProject(
                "ws-1",
                "user-1",
                List.of(originalImage, processingImage, pageXml),
                List.of("legacy/input/0001.png", "legacy/processing/bin/0001.png", "legacy/processing/0001.xml"),
                "legacy"
        );

        assertEquals("project-legacy", result.projectId());
        assertEquals("legacy", result.projectName());
        assertEquals(1, result.pageCount());
        assertEquals(2, result.imageCount());
        assertEquals(1, result.xmlCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Page>> pagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(pageRepository).saveAll(pagesCaptor.capture());
        Page importedPage = pagesCaptor.getValue().iterator().next();
        Set<String> variants = importedPage.getImages().stream()
                .map(PageImage::getVariant)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("original", "bin"), variants);
        verify(pageFilterIndexService).rebuildProjectIndex("project-legacy");
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
}
