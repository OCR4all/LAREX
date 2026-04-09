package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectPackageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicProjectReleaseControllerTest {

    @Mock
    private ProjectPackageService projectPackageService;

    @Test
    void downloadSharedRelease_returnsExpectedHeadersAndDelegatesTracking() throws Exception {
        PublicProjectReleaseController controller = new PublicProjectReleaseController(projectPackageService);
        Path archive = Files.createTempFile("public-project-release-", ".zip");
        Files.writeString(archive, "release-body");

        when(projectPackageService.downloadSharedReleasePackage("share-123", "Bearer secret-token", true))
                .thenReturn(new ProjectPackageService.SharedReleaseDownload(
                        "Project-v1.larex-project.zip",
                        archive,
                        Files.size(archive),
                        "checksum-123"
                ));

        ResponseEntity<org.springframework.core.io.Resource> response = controller.downloadSharedRelease("share-123", "Bearer secret-token");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("private, no-store, max-age=0", response.getHeaders().getCacheControl());
        assertEquals("checksum-123", response.getHeaders().getFirst("X-Checksum-Sha256"));
        assertTrue(response.getBody() instanceof FileSystemResource);
        verify(projectPackageService).downloadSharedReleasePackage("share-123", "Bearer secret-token", true);
    }
}
