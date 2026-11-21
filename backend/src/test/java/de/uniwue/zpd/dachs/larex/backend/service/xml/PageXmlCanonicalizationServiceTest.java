package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.FileSystemResource;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PageXmlCanonicalizationServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private PageXmlVersionService pageXmlVersionService;
    @Mock
    private PageXmlRepository pageXmlRepository;
    @Mock
    private HierarchicalFileStorageService hierarchicalFileStorageService;

    @Test
    void canonicalizeAtIngest_nonPageXml_returnsNoOpOutcome() throws Exception {
        PageXml pageXml = new PageXml();
        pageXml.setSchema(XmlSchema.ALTO_XML);

        PageXmlCanonicalizationService service = new PageXmlCanonicalizationService(
                new PageXmlConversionService(),
                pageXmlVersionService,
                pageXmlRepository,
                hierarchicalFileStorageService
        );

        PageXmlCanonicalizationService.CanonicalizationOutcome outcome =
                service.canonicalizeAtIngest(pageXml, "user-1", "test ingest");

        assertFalse(outcome.converted());
        assertFalse(outcome.snapshotCreated());
        assertEquals(null, outcome.sourceVersion());
        assertEquals(null, outcome.targetVersion());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "UTF-16", "UTF-16LE", "UTF-16BE"})
    void canonicalizeAtIngest_primaryPageXml_isNoOpWithoutSnapshot(String encoding) throws Exception {
        Path xmlPath = tempDir.resolve("primary.xml");
        String xml = compactPrimaryXml().replace("UTF-8", encoding)
                .replace("<PcGts", "<!-- <Example/> -->\n<PcGts")
                .replace("tester", "Tést");
        Files.writeString(xmlPath, xml, Charset.forName(encoding));
        PageXmlValidationService validation = new PageXmlValidationService();
        assertTrue(validation.validatePageXml(new FileSystemResource(xmlPath)).valid());

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-2");
        pageXml.setFilePath("primary.xml");
        pageXml.setSchema(XmlSchema.PAGE_XML);

        when(hierarchicalFileStorageService.resolveUploadPath("primary.xml")).thenReturn(xmlPath);
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);

        PageXmlCanonicalizationService service = new PageXmlCanonicalizationService(
                new PageXmlConversionService(),
                pageXmlVersionService,
                pageXmlRepository,
                hierarchicalFileStorageService
        );

        PageXmlCanonicalizationService.CanonicalizationOutcome outcome =
                service.canonicalizeAtIngest(pageXml, "user-1", "test ingest");

        assertFalse(outcome.converted());
        assertFalse(outcome.snapshotCreated());
        assertEquals("2024-07-15", pageXml.getSchemaVersion());
        String persistedXml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        assertTrue(persistedXml.contains("\n   <Metadata>\n      <Creator>Tést</Creator>"));
        assertTrue(persistedXml.contains("encoding=\"UTF-8\""));
        assertTrue(validation.validatePageXml(new FileSystemResource(xmlPath)).valid());
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(pageXmlRepository).save(pageXml);
    }

    @Test
    void canonicalizeAtIngest_altoXml_isConvertedToPrimaryPageXml() throws Exception {
        Path xmlPath = tempDir.resolve("0001.xml");
        Files.write(xmlPath, new org.springframework.core.io.ClassPathResource("alto/pixel.xml").getContentAsByteArray());

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-alto");
        pageXml.setFilePath("0001.xml");
        pageXml.setSchema(XmlSchema.PAGE_XML);

        when(hierarchicalFileStorageService.resolveUploadPath("0001.xml")).thenReturn(xmlPath);
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);

        PageXmlCanonicalizationService service = new PageXmlCanonicalizationService(
                new PageXmlConversionService(),
                pageXmlVersionService,
                pageXmlRepository,
                hierarchicalFileStorageService
        );

        PageXmlCanonicalizationService.CanonicalizationOutcome outcome =
                service.canonicalizeAtIngest(pageXml, "user-1", "test ingest");

        assertTrue(outcome.converted());
        assertFalse(outcome.snapshotCreated());
        assertEquals(PageXmlCanonicalizationService.ALTO_SOURCE, outcome.sourceVersion());
        assertEquals("2024-07-15", pageXml.getSchemaVersion());
        assertEquals(Files.size(xmlPath), pageXml.getFileSize());
        assertTrue(new PageXmlValidationService().validatePageXml(new FileSystemResource(xmlPath)).valid());
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2017-07-15", "2019-07-15"})
    void canonicalizeAtIngest_olderPageXml_upgradesTo2024AndCreatesSnapshot(String version) throws Exception {
        Path xmlPath = tempDir.resolve("legacy.xml");
        Files.writeString(xmlPath, pageXml(version), StandardCharsets.UTF_8);

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-legacy");
        pageXml.setFilePath("legacy.xml");
        pageXml.setSchema(XmlSchema.PAGE_XML);

        when(hierarchicalFileStorageService.resolveUploadPath("legacy.xml")).thenReturn(xmlPath);
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);

        PageXmlCanonicalizationService service = new PageXmlCanonicalizationService(
                new PageXmlConversionService(),
                pageXmlVersionService,
                pageXmlRepository,
                hierarchicalFileStorageService
        );

        PageXmlCanonicalizationService.CanonicalizationOutcome outcome =
                service.canonicalizeAtIngest(pageXml, "user-1", "async chunked upload");

        assertEquals(version, outcome.sourceVersion());
        assertEquals("2024-07-15", outcome.targetVersion());
        assertEquals(true, outcome.converted());
        assertEquals(true, outcome.snapshotCreated());
        assertEquals("2024-07-15", pageXml.getSchemaVersion());
        assertTrue(Files.readString(xmlPath, StandardCharsets.UTF_8).contains("pagecontent/2024-07-15"));
        verify(pageXmlVersionService).createVersion(
                eq("xml-legacy"),
                eq("user-1"),
                eq("Auto-snapshot before PAGE XML canonicalization from " + version + " to 2024-07-15 (async chunked upload)")
        );
        verify(pageXmlRepository).save(pageXml);
    }

    @Test
    void canonicalizeAtIngest_withoutSnapshot_upgradesWithoutCreatingAVersion() throws Exception {
        Path xmlPath = tempDir.resolve("action-result.xml");
        Files.writeString(xmlPath, pageXml("2019-07-15"), StandardCharsets.UTF_8);

        PageXml pageXml = new PageXml();
        pageXml.setId("xml-action");
        pageXml.setFilePath("action-result.xml");
        pageXml.setSchema(XmlSchema.PAGE_XML);

        when(hierarchicalFileStorageService.resolveUploadPath("action-result.xml")).thenReturn(xmlPath);
        when(pageXmlRepository.save(pageXml)).thenReturn(pageXml);

        PageXmlCanonicalizationService service = new PageXmlCanonicalizationService(
                new PageXmlConversionService(),
                pageXmlVersionService,
                pageXmlRepository,
                hierarchicalFileStorageService
        );

        PageXmlCanonicalizationService.CanonicalizationOutcome outcome =
                service.canonicalizeAtIngest(pageXml, "user-1", "LAREX Action run-1", false);

        assertTrue(outcome.converted());
        assertFalse(outcome.snapshotCreated());
        assertEquals("2024-07-15", pageXml.getSchemaVersion());
        assertTrue(Files.readString(xmlPath, StandardCharsets.UTF_8).contains("pagecontent/2024-07-15"));
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
    }

    private String compactPrimaryXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15\">"
                + "<Metadata><Creator>tester</Creator><Created>2026-03-05T10:00:00</Created>"
                + "<LastChange>2026-03-05T10:00:00</LastChange></Metadata>"
                + "<Page imageFilename=\"img.png\" imageWidth=\"1000\" imageHeight=\"1000\"/></PcGts>";
    }

    private String exported2017Xml() {
        return pageXml("2017-07-15");
    }

    private String pageXml(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/%s">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000"/>
                </PcGts>
                """.formatted(version);
    }
}
