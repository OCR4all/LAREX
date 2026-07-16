package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.ProjectPackageDto;
import de.uniwue.zpd.dachs.larex.backend.controller.project.PublicProjectReleaseController;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageImage;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectPackageRelease;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageImageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectPackageReleaseRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.backup.ArchiveIoService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProjectPackageReleaseIntegrationTest {

    @Autowired
    private ProjectPackageService projectPackageService;

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
    private ProjectPackageReleaseRepository projectPackageReleaseRepository;

    @Autowired
    private PublicProjectReleaseController publicProjectReleaseController;

    @Autowired
    private HierarchicalFileStorageService hierarchicalFileStorageService;

    @MockitoBean
    private WorkspaceAccessService workspaceAccessService;

    @MockitoBean
    private WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @MockitoBean
    private AuthorizationPolicyService authorizationPolicyService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectPackageService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(projectPackageService, "projectReleaseSharePublicBaseUrl", "http://larex.localhost/api/public/project-releases");
        ReflectionTestUtils.setField(hierarchicalFileStorageService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(hierarchicalFileStorageService, "uploadRoot", tempDir.toAbsolutePath().normalize());

        doNothing().when(workspaceAccessService).requireManageProjectsAccess(anyString(), anyString());
        doNothing().when(workspaceAccessService).requireManageProjectReleasesAndSharesAccess(anyString(), anyString());
        doNothing().when(workspaceAccessService).requireWorkspaceAccess(anyString(), anyString());
        when(workspaceQuotaGuardService.reserveBytesOrThrow(anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1, Long.class));
        doNothing().when(workspaceQuotaGuardService).syncUsageAndReleaseReservation(anyString(), org.mockito.ArgumentMatchers.anyLong());
        when(authorizationPolicyService.resolveProjectCapabilities(org.mockito.ArgumentMatchers.any(), anyString()))
                .thenReturn(new AuthorizationCapabilitiesDto.ProjectCapabilities(true, true, true, true, true, true, true, true, true));
    }

    @Test
    void createReleaseFreezesCurrentProjectPackageAndListsReleaseMetadata() throws Exception {
        TestProjectSource source = createProjectSource("ws-project-release");

        ProjectPackageDto.ReleaseSummaryResponse release = projectPackageService.createRelease(
                source.workspaceId(),
                source.project().getId(),
                new ProjectPackageDto.CreateReleaseRequest(
                        null,
                        "Baseline release",
                        "2019-07-15",
                        List.of()
                ),
                "user-1"
        );

        assertEquals(1, release.versionNumber());
        assertEquals("v1", release.versionTag());
        assertEquals(2, release.pageCount());
        assertEquals(ProjectPackageDto.ProjectReleaseStatus.READY, release.status());
        assertNotNull(release.packageChecksumSha256());

        Files.writeString(source.firstXmlPath(), validPageXml("page-a-updated.png"), StandardCharsets.UTF_8);
        Files.writeString(source.secondXmlPath(), validPageXml("page-b-updated.png"), StandardCharsets.UTF_8);

        ProjectPackageService.ReleaseFileDownload download = projectPackageService.downloadReleasePackage(
                source.workspaceId(),
                source.project().getId(),
                release.id(),
                "user-1"
        );
        Path extracted = archiveIoService.extractZipToTempDir(Files.newInputStream(download.absolutePath()), "project-release-download");

        assertTrue(Files.readString(findFileContaining(extracted, "files/xml", source.firstXml().getId())).contains("page-a-v1.png"));
        assertTrue(Files.readString(findFileContaining(extracted, "files/xml", source.secondXml().getId())).contains("page-b-v1.png"));

        List<ProjectPackageDto.ReleaseSummaryResponse> listed = projectPackageService.listReleases(
                source.workspaceId(),
                source.project().getId(),
                "user-1"
        );
        assertEquals(1, listed.size());
        assertEquals(release.id(), listed.getFirst().id());
    }

    @Test
    void createOrRotateShareStoresOnlyHashAndInvalidatesOldSecret() throws Exception {
        TestProjectSource source = createProjectSource("ws-project-share");
        ProjectPackageDto.ReleaseSummaryResponse release = createRelease(source);

        ProjectPackageDto.ReleaseShareResponse firstShare = projectPackageService.createOrRotateReleaseShare(
                source.workspaceId(),
                source.project().getId(),
                release.id(),
                new ProjectPackageDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(7)),
                "user-1"
        );

        ProjectPackageRelease stored = projectPackageReleaseRepository.findById(release.id()).orElseThrow();
        assertNotNull(stored.getShareSecretHash());
        assertFalse(stored.getShareSecretHash().contains(firstShare.secret()));

        ProjectPackageDto.ReleaseShareResponse rotatedShare = projectPackageService.createOrRotateReleaseShare(
                source.workspaceId(),
                source.project().getId(),
                release.id(),
                new ProjectPackageDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(14)),
                "user-1"
        );

        assertNotEquals(firstShare.secret(), rotatedShare.secret());
        assertThrows(
                ResourceNotFoundException.class,
                () -> projectPackageService.downloadSharedReleasePackage(
                        extractSharePublicId(firstShare.downloadUrl()),
                        "Bearer " + firstShare.secret(),
                        true
                )
        );
    }

    @Test
    void publicSharedDownloadRequiresValidBearerTracksUsageAndSupportsHead() throws Exception {
        TestProjectSource source = createProjectSource("ws-project-public");
        ProjectPackageDto.ReleaseSummaryResponse release = createRelease(source);
        ProjectPackageDto.ReleaseShareResponse share = projectPackageService.createOrRotateReleaseShare(
                source.workspaceId(),
                source.project().getId(),
                release.id(),
                new ProjectPackageDto.UpsertReleaseShareRequest(LocalDateTime.now().plusDays(7)),
                "user-1"
        );

        String sharePublicId = extractSharePublicId(share.downloadUrl());
        ResponseEntity<Resource> headResponse = publicProjectReleaseController.headSharedRelease(sharePublicId, "Bearer " + share.secret());
        assertEquals(200, headResponse.getStatusCode().value());
        assertEquals("private, no-store, max-age=0", headResponse.getHeaders().getCacheControl());
        assertEquals(release.packageChecksumSha256(), headResponse.getHeaders().getFirst("X-Checksum-Sha256"));

        ResponseEntity<Resource> getResponse = publicProjectReleaseController.downloadSharedRelease(sharePublicId, "Bearer " + share.secret());
        assertEquals(200, getResponse.getStatusCode().value());
        assert getResponse.getBody() != null;
        Path extracted = archiveIoService.extractZipToTempDir(getResponse.getBody().getInputStream(), "project-public-share");
        assertTrue(Files.readString(findFileContaining(extracted, "files/xml", source.firstXml().getId())).contains("page-a-v1.png"));

        ProjectPackageRelease stored = projectPackageReleaseRepository.findById(release.id()).orElseThrow();
        assertNotNull(stored.getShareLastUsedAt());
        assertEquals(1L, stored.getShareDownloadCount());

        assertThrows(
                ResourceNotFoundException.class,
                () -> projectPackageService.downloadSharedReleasePackage(sharePublicId, "Bearer wrong-secret", true)
        );
    }

    private ProjectPackageDto.ReleaseSummaryResponse createRelease(TestProjectSource source) throws IOException {
        return projectPackageService.createRelease(
                source.workspaceId(),
                source.project().getId(),
                new ProjectPackageDto.CreateReleaseRequest(null, "Shareable project release", "2019-07-15", List.of()),
                "user-1"
        );
    }

    private TestProjectSource createProjectSource(String workspaceId) throws IOException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Library library = libraryRepository.save(new Library(workspaceId, "Library " + suffix));
        Project project = projectRepository.save(new Project("Project " + suffix, null, library));

        Page firstPage = pageRepository.save(new Page("Page A " + suffix, null, project));
        firstPage.setTags(new ArrayList<>(List.of("tag-a")));
        firstPage = pageRepository.save(firstPage);

        Page secondPage = pageRepository.save(new Page("Page B " + suffix, null, project));
        secondPage.setTags(new ArrayList<>(List.of("tag-b")));
        secondPage = pageRepository.save(secondPage);

        Path firstXmlPath = writeUploadFile("project-source/" + suffix + "/page-a.xml", validPageXml("page-a-v1.png"));
        Path firstImagePath = writeUploadFile("project-source/" + suffix + "/page-a.png", "img-first-v1");
        Path secondXmlPath = writeUploadFile("project-source/" + suffix + "/page-b.xml", validPageXml("page-b-v1.png"));
        Path secondImagePath = writeUploadFile("project-source/" + suffix + "/page-b.png", "img-second-v1");

        PageXml firstXml = pageXmlRepository.save(new PageXml(
                "page-a.xml",
                relativeToUploadRoot(firstXmlPath),
                "application/xml",
                Files.size(firstXmlPath),
                "main",
                "page-a",
                XmlSchema.PAGE_XML,
                "2019-07-15",
                firstPage
        ));
        PageXml secondXml = pageXmlRepository.save(new PageXml(
                "page-b.xml",
                relativeToUploadRoot(secondXmlPath),
                "application/xml",
                Files.size(secondXmlPath),
                "main",
                "page-b",
                XmlSchema.PAGE_XML,
                "2019-07-15",
                secondPage
        ));

        pageImageRepository.save(new PageImage(
                "page-a.png",
                relativeToUploadRoot(firstImagePath),
                "image/png",
                Files.size(firstImagePath),
                "color",
                "page-a",
                firstPage
        ));
        pageImageRepository.save(new PageImage(
                "page-b.png",
                relativeToUploadRoot(secondImagePath),
                "image/png",
                Files.size(secondImagePath),
                "color",
                "page-b",
                secondPage
        ));

        return new TestProjectSource(workspaceId, project, firstXml, secondXml, firstXmlPath, secondXmlPath);
    }

    private Path writeUploadFile(String relativePath, String content) throws IOException {
        Path filePath = tempDir.resolve(relativePath);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        return filePath;
    }

    private String relativeToUploadRoot(Path path) {
        return tempDir.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Path findFileContaining(Path root, String prefix, String idFragment) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        return relative.startsWith(prefix + "/") && relative.contains(idFragment);
                    }).min(Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new IllegalStateException("No archive file found for " + prefix + " and " + idFragment));
        }
    }

    private String extractSharePublicId(String downloadUrl) {
        String marker = "/project-releases/";
        int start = downloadUrl.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("No project release share ID in URL: " + downloadUrl);
        }
        String rest = downloadUrl.substring(start + marker.length());
        int end = rest.indexOf("/download");
        if (end < 0) {
            throw new IllegalStateException("No download suffix in URL: " + downloadUrl);
        }
        return rest.substring(0, end);
    }

    private String validPageXml(String imageFilename) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="%s" imageWidth="1000" imageHeight="1000"/>
                </PcGts>
                """.formatted(imageFilename);
    }

    private record TestProjectSource(
            String workspaceId,
            Project project,
            PageXml firstXml,
            PageXml secondXml,
            Path firstXmlPath,
            Path secondXmlPath
    ) {
    }
}
