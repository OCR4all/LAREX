package de.uniwue.zpd.dachs.larex.backend.service.version;

import de.uniwue.zpd.dachs.larex.backend.config.UploadDirectoryProperties;
import de.uniwue.zpd.dachs.larex.backend.config.VersioningProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlVersionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaRefreshService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void restoreVersionPrettyPrintsRestoredPageXml() throws Exception {
        PageXmlVersionService service = service();
        PageXmlVersion version = version("version-1", "xml-1", "xml/versions/xml-1/1.xml");
        PageXml xml = version.getPageXml();
        xml.setFilePath("xml/current.xml");

        Path versionPath = tempDir.resolve(version.getFilePath());
        Path currentPath = tempDir.resolve(xml.getFilePath());
        Files.createDirectories(versionPath.getParent());
        Files.createDirectories(currentPath.getParent());
        Files.writeString(versionPath, "<PcGts><Metadata><Creator>tester</Creator></Metadata><Page/></PcGts>");
        Files.writeString(currentPath, "<PcGts/>");

        when(versionRepository.findById("version-1")).thenReturn(Optional.of(version));
        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(xml));
        when(versionRepository.findMaxVersionNumber("xml-1")).thenReturn(1);
        when(versionRepository.save(any(PageXmlVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.restoreVersion("version-1", "xml-1", "user-1");

        assertTrue(Files.readString(currentPath).contains("\n   <Metadata>\n      <Creator>tester</Creator>"));
    }

    @Test
    void restoreVersionUpgradesAnOlderVersionToPrimaryAndKeepsTheVersionFile() throws Exception {
        PageXmlVersionService service = service();
        PageXmlVersion version = version("version-1", "xml-1", "xml/versions/xml-1/1.xml");
        PageXml xml = version.getPageXml();
        xml.setFilePath("xml/current.xml");
        xml.setSchema(XmlSchema.PAGE_XML);
        xml.setSchemaVersion("2024-07-15");

        Path versionPath = tempDir.resolve(version.getFilePath());
        Path currentPath = tempDir.resolve(xml.getFilePath());
        Files.createDirectories(versionPath.getParent());
        Files.createDirectories(currentPath.getParent());
        String version2019 = pageXml("2019-07-15", "Restored text");
        Files.writeString(versionPath, version2019);
        Files.writeString(currentPath, pageXml("2024-07-15", "Current text"));

        when(versionRepository.findById("version-1")).thenReturn(Optional.of(version));
        when(pageXmlRepository.findById("xml-1")).thenReturn(Optional.of(xml));
        when(versionRepository.findMaxVersionNumber("xml-1")).thenReturn(1);
        when(versionRepository.save(any(PageXmlVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.restoreVersion("version-1", "xml-1", "user-1");

        String restored = Files.readString(currentPath);
        assertTrue(restored.contains("pagecontent/2024-07-15"));
        assertFalse(restored.contains("pagecontent/2019-07-15"));
        assertTrue(restored.contains("Restored text"));
        assertEquals("2024-07-15", xml.getSchemaVersion());
        assertEquals(Files.size(currentPath), xml.getFileSize());
        assertEquals(version2019, Files.readString(versionPath));
        assertTrue(Files.readString(tempDir.resolve("xml/versions/xml-1/2.xml")).contains("Current text"));
    }

    private static String pageXml(String version, String text) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/%s">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000">
                    <TextRegion id="r1">
                      <Coords points="10,10 100,10 100,100 10,100"/>
                      <TextEquiv><Unicode>%s</Unicode></TextEquiv>
                    </TextRegion>
                  </Page>
                </PcGts>
                """.formatted(version, text);
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
                new UploadPathService(uploadDirectoryProperties),
                new PageXmlConversionService()
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
