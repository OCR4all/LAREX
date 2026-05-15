package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.service.export.DocumentExportService;
import de.uniwue.zpd.dachs.larex.backend.service.importer.IiifImportService;
import de.uniwue.zpd.dachs.larex.backend.service.project.LegacyOcr4allImportService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectService;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadConflictService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectControllerExportTest {

    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectTransferService projectTransferService;
    @Mock
    private ProjectReadService projectReadService;
    @Mock
    private ProjectPackageService projectPackageService;
    @Mock
    private LegacyOcr4allImportService legacyOcr4allImportService;
    @Mock
    private DocumentExportService documentExportService;
    @Mock
    private IiifImportService iiifImportService;
    @Mock
    private UploadConflictService uploadConflictService;

    @Test
    void exportProjectOutput_returnsAttachmentHeadersAndBody() throws Exception {
        ProjectController controller = new ProjectController(
                projectService,
                projectTransferService,
                projectReadService,
                projectPackageService,
                legacyOcr4allImportService,
                documentExportService,
                iiifImportService,
                uploadConflictService
        );

        byte[] body = "hello".getBytes();
        var request = new DocumentExportDto.ProjectExportRequest(DocumentExportDto.ExportFormat.TXT, null, null, true, null, null, null, null, null, null);
        when(documentExportService.exportProject("ws-1", "project-1", "user-1", request))
                .thenReturn(new DocumentExportService.DocumentExportResult("Project.txt", "text/plain", body));

        ResponseEntity<byte[]> response = controller.exportProjectOutput("ws-1", "project-1", request, "user-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("text/plain", response.getHeaders().getContentType().toString());
        assertEquals("attachment; filename=\"Project.txt\"", response.getHeaders().getFirst("Content-Disposition"));
        assertArrayEquals(body, response.getBody());
    }

    @Test
    void downloadRelease_returnsAttachmentHeadersAndStreamingBody() throws Exception {
        ProjectController controller = new ProjectController(
                projectService,
                projectTransferService,
                projectReadService,
                projectPackageService,
                legacyOcr4allImportService,
                documentExportService,
                iiifImportService,
                uploadConflictService
        );

        Path archive = Files.createTempFile("project-release-", ".zip");
        Files.writeString(archive, "release-body");
        when(projectPackageService.downloadReleasePackage("ws-1", "project-1", "release-1", "user-1"))
                .thenReturn(new ProjectPackageService.ReleaseFileDownload(
                        "Project-v1.larex-project.zip",
                        archive,
                        Files.size(archive),
                        "abc123"
                ));

        ResponseEntity<org.springframework.core.io.Resource> response = controller.downloadRelease("ws-1", "project-1", "release-1", "user-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("attachment; filename=\"Project-v1.larex-project.zip\"", response.getHeaders().getFirst("Content-Disposition"));
        assertEquals("abc123", response.getHeaders().getFirst("X-Checksum-Sha256"));
        assertTrue(response.getBody() instanceof FileSystemResource);
    }
}
