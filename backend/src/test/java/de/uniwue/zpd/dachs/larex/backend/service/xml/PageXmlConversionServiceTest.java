package de.uniwue.zpd.dachs.larex.backend.service.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PageXmlConversionServiceTest {

    @TempDir
    Path tempDir;

    private final PageXmlConversionService service = new PageXmlConversionService();

    @Test
    void getSupportedExportVersions_containsExpectedTargets() {
        assertTrue(service.getSupportedExportVersions().contains("2010-03-19"));
        assertTrue(service.getSupportedExportVersions().contains("2019-07-15"));
    }

    @Test
    void convertFileInPlace_primarySource_isNoOp() throws Exception {
        Path xmlPath = tempDir.resolve("current.xml");
        Files.writeString(xmlPath, exported2019Xml(), StandardCharsets.UTF_8);

        PageXmlConversionService.ConversionOutcome outcome =
                service.convertFileInPlace(xmlPath, PageXmlConversionService.PRIMARY_PAGE_VERSION);

        assertFalse(outcome.converted());
        assertEquals("2019-07-15", outcome.sourceVersion());
        assertEquals("2019-07-15", service.detectPageVersion(xmlPath));
    }

    @Test
    void convertFileToVersion_sameVersion_returnsOriginalBytes() throws Exception {
        Path xmlPath = tempDir.resolve("source.xml");
        Files.writeString(xmlPath, exported2019Xml(), StandardCharsets.UTF_8);

        byte[] bytes = service.convertFileToVersion(xmlPath, "2019-07-15");
        assertEquals(Files.readString(xmlPath), new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    void convertFileInPlace_legacy2017_convertsTo2019WithoutEmptyAttributes() throws Exception {
        Path xmlPath = tempDir.resolve("legacy-2017.xml");
        Files.writeString(xmlPath, exported2017Xml(), StandardCharsets.UTF_8);

        PageXmlConversionService.ConversionOutcome outcome =
                service.convertFileInPlace(xmlPath, PageXmlConversionService.PRIMARY_PAGE_VERSION);

        assertTrue(outcome.converted());
        assertEquals("2017-07-15", outcome.sourceVersion());
        assertEquals("2019-07-15", outcome.targetVersion());
        assertEquals("2019-07-15", service.detectPageVersion(xmlPath));

        String convertedXml = Files.readString(xmlPath, StandardCharsets.UTF_8);
        assertNotNull(convertedXml);
        assertTrue(convertedXml.contains("pagecontent/2019-07-15"));
        assertFalse(convertedXml.contains("=\"\""));
    }

    private String exported2019Xml() throws Exception {
        return pageXml("2019-07-15");
    }

    private String exported2017Xml() throws Exception {
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
