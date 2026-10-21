package de.uniwue.zpd.dachs.larex.backend.service.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PageXmlConversionServiceTest {

    @TempDir
    Path tempDir;

    private final PageXmlConversionService service = new PageXmlConversionService();

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

    @ParameterizedTest
    @ValueSource(strings = { "<PcGts/>", "<PcGts><Page></PcGts>",
            "<PcGts xmlns=\"http://schema.primaresearch.org/PAGE/gts/pagecontent/2099-01-01\"/>" })
    void exportUnparseableXmlPreservesOriginalBytesAndReportsSkippedConversion(String xml) throws Exception {
        Path source = tempDir.resolve("broken.xml");
        byte[] original = xml.getBytes(StandardCharsets.UTF_8);
        Files.write(source, original);
        var warnings = new ArrayList<String>();
        var output = new ByteArrayOutputStream();

        service.writeFileToVersion(source, "2017-07-15", output, warnings::add);

        assertArrayEquals(original, output.toByteArray());
        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("original PAGE XML without conversion to 2017-07-15"));
        assertThrows(IOException.class, () -> service.convertFileInPlace(source, "2019-07-15"));
    }

    @Test
    void failedDownloadDoesNotAppendOriginalXmlToPartialConvertedOutput() throws Exception {
        Path source = tempDir.resolve("page.xml");
        Files.writeString(source, exported2019Xml());
        var warnings = new ArrayList<String>();
        IOException disconnected = new IOException("Client disconnected");
        var output = new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw disconnected;
            }
        };

        assertSame(disconnected, assertThrows(IOException.class,
                () -> service.writeFileToVersion(source, "2017-07-15", output, warnings::add)));
        assertTrue(warnings.isEmpty());
        try (var files = Files.list(tempDir)) {
            assertEquals(java.util.List.of(source), files.toList());
        }
    }

    @Test
    void exportStillConvertsValidXmlToRequestedVersion() throws Exception {
        Path source = tempDir.resolve("page.xml");
        Files.writeString(source, exported2019Xml());
        var output = new ByteArrayOutputStream();
        var warnings = new ArrayList<String>();

        service.writeFileToVersion(source, "2017-07-15", output, warnings::add);

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("pagecontent/2017-07-15"));
        assertTrue(warnings.isEmpty());
        assertEquals(exported2019Xml(), Files.readString(source));
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
