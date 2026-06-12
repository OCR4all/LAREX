package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.controller.dataset.PublicDatasetReleaseController;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetRelease;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetReleaseRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Autowired
    private DatasetReleaseRepository datasetReleaseRepository;

    @Autowired
    private PublicDatasetReleaseController publicDatasetReleaseController;

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
        ReflectionTestUtils.setField(datasetService, "datasetReleaseSharePublicBaseUrl", "http://larex.localhost/api/public/dataset-releases");

        doNothing().when(workspaceAccessService).requireManageProjectsAccess(anyString(), anyString());
        doNothing().when(workspaceAccessService).requireManageProjectReleasesAndSharesAccess(anyString(), anyString());
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

        byte[] archive = writeDatasetPackage(source.workspaceId(), dataset.id());
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

        byte[] archive = writeDatasetPackage(source.workspaceId(), dataset.id());
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
                () -> writeDatasetPackage(source.workspaceId(), dataset.id())
        );
        assertTrue(thrown.getMessage().contains("broken items"));
    }

    @Test
    void createReleaseFreezesCurrentDatasetPackageAndListsReleaseMetadata() throws Exception {
        TestSourcePage source = createSourcePage("ws-release", "release-page", "xml-release-v1", "img-release-v1");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Release dataset");

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

        DatasetDto.ReleaseSummaryResponse release = datasetService.createRelease(
                source.workspaceId(),
                dataset.id(),
                new DatasetDto.CreateReleaseRequest(null, "First frozen release"),
                "user-1"
        );

        assertEquals(1, release.versionNumber());
        assertEquals("v1", release.versionTag());
        assertEquals(DatasetDto.DatasetReleaseStatus.READY, release.status());
        assertNotNull(release.packageChecksumSha256());

        Files.writeString(source.xmlPath(), "xml-release-v2", StandardCharsets.UTF_8);
        Files.writeString(source.imagePath(), "img-release-v2", StandardCharsets.UTF_8);

        DatasetService.ReleaseFileDownload download = datasetService.downloadReleasePackage(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                "user-1"
        );
        Path extracted = archiveIoService.extractZipToTempDir(Files.newInputStream(download.absolutePath()), "dataset-release-download");

        assertEquals("xml-release-v1", Files.readString(findSingleFile(extracted, "files/xml")));
        assertEquals("img-release-v1", Files.readString(findSingleFile(extracted, "files/images")));

        DatasetDto.DetailResponse refreshedDataset = datasetService.getDataset(source.workspaceId(), dataset.id(), "user-1");
        assertEquals(1, refreshedDataset.releases().size());
        assertEquals(release.id(), refreshedDataset.releases().getFirst().id());
    }

    @Test
    void createShareReturnsOneTimeSecretAndStoresOnlyHash() throws Exception {
        TestSourcePage source = createSourcePage("ws-share", "share-page", "xml-share", "img-share");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Share dataset");
        DatasetDto.ReleaseSummaryResponse release = createReleaseFromDataset(dataset.id(), source);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        DatasetDto.ReleaseShareResponse share = datasetService.createOrRotateReleaseShare(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                new DatasetDto.UpsertReleaseShareRequest(expiresAt),
                "user-1"
        );

        assertTrue(share.downloadUrl().contains("/api/public/dataset-releases/"));
        assertNotNull(share.secret());
        assertEquals(expiresAt, share.expiresAt());

        DatasetRelease stored = datasetReleaseRepository.findById(release.id()).orElseThrow();
        assertNotEquals(share.secret(), stored.getShareSecretHash());
        assertEquals(share.secret().substring(0, 8), stored.getShareSecretPrefix());
        assertNotNull(stored.getShareCreatedAt());
        assertEquals(0L, stored.getShareDownloadCount());
    }

    @Test
    void rotatingAndRevokingShareInvalidatePreviousSecrets() throws Exception {
        TestSourcePage source = createSourcePage("ws-share-rotate", "share-rotate-page", "xml-rotate", "img-rotate");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Share rotation dataset");
        DatasetDto.ReleaseSummaryResponse release = createReleaseFromDataset(dataset.id(), source);

        DatasetDto.ReleaseShareResponse firstShare = datasetService.createOrRotateReleaseShare(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                new DatasetDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(3)),
                "user-1"
        );
        String firstShareId = extractSharePublicId(firstShare.downloadUrl());

        DatasetDto.ReleaseShareResponse secondShare = datasetService.createOrRotateReleaseShare(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                new DatasetDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(5)),
                "user-1"
        );
        String secondShareId = extractSharePublicId(secondShare.downloadUrl());

        assertThrows(ResourceNotFoundException.class, () ->
                datasetService.downloadSharedReleasePackage(firstShareId, "Bearer " + firstShare.secret(), true));

        DatasetService.SharedReleaseDownload download = datasetService.downloadSharedReleasePackage(
                secondShareId,
                "Bearer " + secondShare.secret(),
                true
        );
        assertTrue(Files.exists(download.absolutePath()));

        datasetService.revokeReleaseShare(source.workspaceId(), dataset.id(), release.id(), "user-1");

        assertThrows(ResourceNotFoundException.class, () ->
                datasetService.downloadSharedReleasePackage(secondShareId, "Bearer " + secondShare.secret(), true));
    }

    @Test
    void publicShareDownloadEndpointStreamsHeadersAndTracksUsage() throws Exception {
        TestSourcePage source = createSourcePage("ws-share-public", "share-public-page", "xml-public", "img-public");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Public share dataset");
        DatasetDto.ReleaseSummaryResponse release = createReleaseFromDataset(dataset.id(), source);

        DatasetDto.ReleaseShareResponse share = datasetService.createOrRotateReleaseShare(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                new DatasetDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(4)),
                "user-1"
        );
        String shareId = extractSharePublicId(share.downloadUrl());

        ResponseEntity<Resource> headResponse = publicDatasetReleaseController.headSharedRelease(
                shareId,
                "Bearer " + share.secret()
        );
        assertEquals(200, headResponse.getStatusCode().value());
        assertEquals("private, no-store, max-age=0", headResponse.getHeaders().getCacheControl());
        assertNotNull(headResponse.getHeaders().getFirst("X-Checksum-Sha256"));
        assertNotNull(headResponse.getHeaders().getContentDisposition().getFilename());

        DatasetRelease beforeGet = datasetReleaseRepository.findById(release.id()).orElseThrow();
        assertEquals(0L, beforeGet.getShareDownloadCount());
        assertEquals(null, beforeGet.getShareLastUsedAt());

        ResponseEntity<Resource> getResponse = publicDatasetReleaseController.downloadSharedRelease(
                shareId,
                "Bearer " + share.secret()
        );
        assertEquals(200, getResponse.getStatusCode().value());
        assertEquals("private, no-store, max-age=0", getResponse.getHeaders().getCacheControl());
        assertNotNull(getResponse.getHeaders().getFirst("X-Checksum-Sha256"));
        assertTrue(getResponse.getHeaders().getContentLength() > 0);

        DatasetRelease afterGet = datasetReleaseRepository.findById(release.id()).orElseThrow();
        assertEquals(1L, afterGet.getShareDownloadCount());
        assertNotNull(afterGet.getShareLastUsedAt());
    }

    @Test
    void sharedReleaseDownloadRejectsExpiredOrMissingSecrets() throws Exception {
        TestSourcePage source = createSourcePage("ws-share-expired", "share-expired-page", "xml-expired", "img-expired");
        DatasetDto.DetailResponse dataset = createDataset(source.workspaceId(), "Expired share dataset");
        DatasetDto.ReleaseSummaryResponse release = createReleaseFromDataset(dataset.id(), source);

        DatasetDto.ReleaseShareResponse share = datasetService.createOrRotateReleaseShare(
                source.workspaceId(),
                dataset.id(),
                release.id(),
                new DatasetDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(1)),
                "user-1"
        );
        String shareId = extractSharePublicId(share.downloadUrl());

        assertThrows(ResourceNotFoundException.class, () ->
                datasetService.downloadSharedReleasePackage(shareId, null, true));
        assertThrows(ResourceNotFoundException.class, () ->
                datasetService.downloadSharedReleasePackage(shareId, "Bearer wrong-secret", true));

        DatasetRelease stored = datasetReleaseRepository.findById(release.id()).orElseThrow();
        stored.setShareExpiresAt(LocalDateTime.now().minusMinutes(1));
        datasetReleaseRepository.save(stored);

        assertThrows(ResourceNotFoundException.class, () ->
                datasetService.downloadSharedReleasePackage(shareId, "Bearer " + share.secret(), true));
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

    private DatasetDto.ReleaseSummaryResponse createReleaseFromDataset(String datasetId, TestSourcePage source) throws Exception {
        datasetService.addItems(
                source.workspaceId(),
                datasetId,
                new DatasetDto.AddItemsRequest(new ArrayList<>(List.of(new DatasetDto.AddItemRequest(
                        source.project().getId(),
                        source.page().getId(),
                        DatasetItem.Mode.LINK,
                        source.xml().getId(),
                        new ArrayList<>(List.of(source.image().getId()))
                )))),
                "user-1"
        );

        return datasetService.createRelease(
                source.workspaceId(),
                datasetId,
                new DatasetDto.CreateReleaseRequest(null, "Shareable release"),
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

    private byte[] writeDatasetPackage(String workspaceId, String datasetId) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        datasetService.writeDatasetPackage(workspaceId, datasetId, "user-1", outputStream);
        return outputStream.toByteArray();
    }

    private String extractSharePublicId(String downloadUrl) {
        String marker = "/dataset-releases/";
        int start = downloadUrl.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("No dataset release share ID in URL: " + downloadUrl);
        }
        String rest = downloadUrl.substring(start + marker.length());
        int end = rest.indexOf("/download");
        if (end < 0) {
            throw new IllegalStateException("No download suffix in URL: " + downloadUrl);
        }
        return rest.substring(0, end);
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
