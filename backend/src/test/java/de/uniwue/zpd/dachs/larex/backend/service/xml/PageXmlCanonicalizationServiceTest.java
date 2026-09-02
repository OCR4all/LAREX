package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageXmlRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void canonicalizeAtIngest_primaryPageXml_isNoOpWithoutSnapshot() throws Exception {
        Path xmlPath = tempDir.resolve("primary.xml");
        Files.writeString(xmlPath, compact2019Xml(), StandardCharsets.UTF_8);

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
        assertEquals("2019-07-15", pageXml.getSchemaVersion());
        assertTrue(Files.readString(xmlPath).contains("\n   <Metadata>\n      <Creator>tester</Creator>"));
        verify(pageXmlVersionService, never()).createVersion(any(), any(), any());
        verify(pageXmlRepository).save(pageXml);
    }

    @Test
    void canonicalizeAtIngest_legacyPageXml_convertsAndCreatesSnapshot() throws Exception {
        Path xmlPath = tempDir.resolve("legacy.xml");
        Files.writeString(xmlPath, exported2017Xml(), StandardCharsets.UTF_8);

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

        assertEquals("2017-07-15", outcome.sourceVersion());
        assertEquals("2019-07-15", outcome.targetVersion());
        assertEquals(true, outcome.converted());
        assertEquals(true, outcome.snapshotCreated());
        assertEquals("2019-07-15", pageXml.getSchemaVersion());
        verify(pageXmlVersionService).createVersion(
                eq("xml-legacy"),
                eq("user-1"),
                eq("Auto-snapshot before PAGE XML canonicalization from 2017-07-15 to 2019-07-15 (async chunked upload)")
        );
        verify(pageXmlRepository).save(pageXml);
    }

    private String compact2019Xml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15\">"
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
