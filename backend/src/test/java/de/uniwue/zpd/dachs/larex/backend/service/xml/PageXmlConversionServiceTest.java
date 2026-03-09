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
        return fixtureFromRepo("uploads/xml/versions/04321d9b-e0c5-4dcd-9b8c-17eb694f127d/1.xml");
    }

    private String exported2017Xml() throws Exception {
        return fixtureFromRepo("uploads/xml/versions/b2be1140-fb1b-491a-8151-ea82afaaebe4/1.xml");
    }

    private String fixtureFromRepo(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
