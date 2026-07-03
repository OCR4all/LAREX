package de.uniwue.zpd.dachs.larex.backend.service.version;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.config.VersioningProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageXmlVersionServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private PageXmlVersionRepository versionRepository;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private WorkspaceQuotaRefreshService workspaceQuotaRefreshService;
    @Mock
    private UserService userService;

    @Test
    void resolveVersionPathReturnsVersionPathForMatchingXml() throws Exception {
        PageXmlVersionService service = service();
        PageXmlVersion version = version("version-1", "xml-1", "xml/versions/xml-1/1.xml");
        Path versionPath = tempDir.resolve(version.getFilePath());
        Files.createDirectories(versionPath.getParent());
        Files.writeString(versionPath, "<PcGts/>");

        when(versionRepository.findById("version-1")).thenReturn(Optional.of(version));

        assertEquals(versionPath, service.resolveVersionPath("version-1", "xml-1"));
    }

    @Test
    void resolveVersionPathRejectsVersionFromAnotherXml() {
        PageXmlVersionService service = service();
        PageXmlVersion version = version("version-1", "xml-other", "xml/versions/xml-other/1.xml");

        when(versionRepository.findById("version-1")).thenReturn(Optional.of(version));

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveVersionPath("version-1", "xml-1"));
    }

    @Test
    void resolveVersionPathFailsWhenVersionFileIsMissing() {
        PageXmlVersionService service = service();
        PageXmlVersion version = version("version-1", "xml-1", "xml/versions/xml-1/1.xml");

        when(versionRepository.findById("version-1")).thenReturn(Optional.of(version));

        assertThrows(IOException.class,
                () -> service.resolveVersionPath("version-1", "xml-1"));
    }

    private PageXmlVersionService service() {
        UploadDirectoryProperties uploadDirectoryProperties = new UploadDirectoryProperties();
        uploadDirectoryProperties.setRootDirectory(tempDir);
        return new PageXmlVersionService(
                versionRepository,
                pageXmlRepository,
                applicationEventPublisher,
                workspaceQuotaRefreshService,
                userService,
                new VersioningProperties(),
                new UploadPathService(uploadDirectoryProperties)
        );
    }

    private PageXmlVersion version(String versionId, String xmlId, String filePath) {
        PageXml xml = new PageXml();
        xml.setId(xmlId);

        PageXmlVersion version = new PageXmlVersion(xml, 1, filePath, 8L, "user-1", "Saved");
        version.setId(versionId);
        return version;
    }
}
