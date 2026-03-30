package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatasetServiceIntegrationTest {

    @Autowired
    private DatasetService datasetService;

    @Autowired
    private ArchiveIoService archiveIoService;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private PageXmlRepository pageXmlRepository;

    @Autowired
    private PageImageRepository pageImageRepository;

    @MockBean
    private WorkspaceAccessService workspaceAccessService;

    @MockBean
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @MockBean
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;

    @MockBean
    private AuthorizationPolicyService authorizationPolicyService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(datasetService, "uploadDir", tempDir.toString());

        doNothing().when(workspaceAccessService).requireManageProjectsAccess(anyString(), anyString());
        doNothing().when(workspaceAccessService).requireWorkspaceAccess(anyString(), anyString());
        when(workspaceQuotaGuardService.reserveBytesOrThrow(anyString(), anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, Long.class));
        doNothing().when(workspaceQuotaGuardService).syncUsageAndReleaseReservation(anyString(), anyLong());
        doNothing().when(workspaceQuotaRefreshService).scheduleUsageRefresh(anyString());
        when(authorizationPolicyService.resolveDatasetCapabilities(anyString(), anyString()))
                .thenReturn(new AuthorizationCapabilitiesDto.DatasetCapabilities(true, true, true, true, true));
    }

    @Test
    void exportDatasetPackageUsesLatestSourceFilesForLinkedItems() throws Exception {
        TestSourcePage source = createSourcePage("ws-link", "link-page", "xml-v1", "img-v1");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Linked dataset");

        datasetService.addItems(
                source.workspaceId(),
                dataset.id(),
                new DatasetDto.AddItemsRequest(new ArrayList<>(List.of(new DatasetDto.AddItemRequest(
                        source.project().getId(),
                        source.page().getId(),
                        DatasetItem.Mode.LINK,
                        source.xml().getId(),
                        new ArrayList<>(List.of(source.image().getId()))
                )))),
                "user-1"
        );

        Files.writeString(source.xmlPath(), "xml-v2", StandardCharsets.UTF_8);
        Files.writeString(source.imagePath(), "img-v2", StandardCharsets.UTF_8);

        byte[] archive = datasetService.exportDatasetPackage(source.workspaceId(), dataset.id(), "user-1");
        Path extracted = archiveIoService.extractZipToTempDir(new ByteArrayInputStream(archive), "dataset-link-export");

        assertEquals("xml-v2", Files.readString(findSingleFile(extracted, "files/xml")));
        assertEquals("img-v2", Files.readString(findSingleFile(extracted, "files/images")));
    }

    @Test
    void exportDatasetPackageKeepsFrozenFilesForCopiedItems() throws Exception {
        TestSourcePage source = createSourcePage("ws-copy", "copy-page", "xml-original", "img-original");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Copied dataset");

        DatasetDto.DetailResponse updated = datasetService.addItems(
                source.workspaceId(),
                dataset.id(),
                new DatasetDto.AddItemsRequest(new ArrayList<>(List.of(new DatasetDto.AddItemRequest(
                        source.project().getId(),
                        source.page().getId(),
                        DatasetItem.Mode.COPY,
                        source.xml().getId(),
                        new ArrayList<>(List.of(source.image().getId()))
                )))),
                "user-1"
        );

        assertNotNull(updated.items().getFirst().copiedAt());

        Files.writeString(source.xmlPath(), "xml-mutated", StandardCharsets.UTF_8);
        Files.writeString(source.imagePath(), "img-mutated", StandardCharsets.UTF_8);

        byte[] archive = datasetService.exportDatasetPackage(source.workspaceId(), dataset.id(), "user-1");
        Path extracted = archiveIoService.extractZipToTempDir(new ByteArrayInputStream(archive), "dataset-copy-export");

        assertEquals("xml-original", Files.readString(findSingleFile(extracted, "files/xml")));
        assertEquals("img-original", Files.readString(findSingleFile(extracted, "files/images")));
    }

    @Test
    void validateAndExportFailWhenLinkedSourceAnnotationDisappears() throws Exception {
        TestSourcePage source = createSourcePage("ws-broken", "broken-page", "xml-stable", "img-stable");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Broken dataset");

        datasetService.addItems(
                source.workspaceId(),
                dataset.id(),
                new DatasetDto.AddItemsRequest(new ArrayList<>(List.of(new DatasetDto.AddItemRequest(
                        source.project().getId(),
                        source.page().getId(),
                        DatasetItem.Mode.LINK,
                        source.xml().getId(),
                        new ArrayList<>(List.of(source.image().getId()))
                )))),
                "user-1"
        );

        pageXmlRepository.delete(source.xml());
        pageXmlRepository.flush();

        DatasetDto.ValidationResponse validation = datasetService.validateDataset(source.workspaceId(), dataset.id(), "user-1");
        assertEquals(de.uniwue.zpd.dachs.larex.backend.entity.Dataset.ValidationStatus.INVALID, validation.status());
        assertFalse(validation.issues().isEmpty());
        assertTrue(validation.issues().getFirst().reason().contains("annotation"));

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> datasetService.exportDatasetPackage(source.workspaceId(), dataset.id(), "user-1")
        );
        assertTrue(thrown.getMessage().contains("broken items"));
    }

    private DatasetDto.DetailResponse createDataset(String workspaceId, String name) {
        return datasetService.createDataset(
                workspaceId,
                new DatasetDto.CreateOrUpdateRequest(
                        name,
                        "Dataset description",
                        new ArrayList<>(List.of("training")),
                        de.uniwue.zpd.dachs.larex.backend.entity.Dataset.SplitTemplate.TRAIN_VAL_TEST,
                        de.uniwue.zpd.dachs.larex.backend.entity.Dataset.SplitAlgorithm.RANDOM_SEEDED,
                        42L,
                        70,
                        15,
                        15,
                        new ArrayList<>()
                ),
                "user-1"
        );
    }

    private TestSourcePage createSourcePage(String workspaceId,
                                            String pageName,
                                            String xmlContent,
                                            String imageContent) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Path sourceRoot = tempDir.resolve(workspaceId + "-" + suffix);
        Files.createDirectories(sourceRoot);

        Library library = libraryRepository.save(new Library(workspaceId, "Library " + suffix));
        Project project = projectRepository.save(new Project("Project " + suffix, null, library));
        Page page = pageRepository.save(new Page(pageName + "-" + suffix, null, project));
        page.setTags(new ArrayList<>(List.of("tag-a", "tag-b")));
        page = pageRepository.save(page);

        Path xmlPath = sourceRoot.resolve("page.xml");
        Path imagePath = sourceRoot.resolve("page.png");
        Files.writeString(xmlPath, xmlContent, StandardCharsets.UTF_8);
        Files.writeString(imagePath, imageContent, StandardCharsets.UTF_8);

        PageXml xml = pageXmlRepository.save(new PageXml(
                "page.xml",
                xmlPath.toString(),
                "application/xml",
                Files.size(xmlPath),
                "main",
                "page",
                XmlSchema.PAGE_XML,
                "2019-07-15",
                page
        ));

        PageImage image = pageImageRepository.save(new PageImage(
                "page.png",
                imagePath.toString(),
                "image/png",
                Files.size(imagePath),
                "color",
                "page",
                page
        ));

        return new TestSourcePage(workspaceId, project, page, xml, image, xmlPath, imagePath);
    }

    private Path findSingleFile(Path root, String prefix) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> root.relativize(path).toString().replace('\\', '/').startsWith(prefix + "/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No archive file found for " + prefix));
        }
    }

    private record TestSourcePage(
            String workspaceId,
            Project project,
            Page page,
            PageXml xml,
            PageImage image,
            Path xmlPath,
            Path imagePath
    ) {}
}
