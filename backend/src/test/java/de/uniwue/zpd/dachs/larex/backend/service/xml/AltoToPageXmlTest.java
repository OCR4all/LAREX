package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.pagexml.PageXml;
import de.uniwue.zpd.dachs.larex.backend.exception.InvalidPageXmlException;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AltoToPageXmlTest {

    private final PageXmlValidationService validation = new PageXmlValidationService();

    @TempDir
    Path tempDir;

    @Test
    void recognisesAltoButNotPageXml() throws IOException {
        assertThat(AltoToPageXml.isAlto(fixture("pixel.xml"))).isTrue();
        assertThat(AltoToPageXml.isAlto(primaryPageXml().getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(AltoToPageXml.isAlto("not xml".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void convertsPixelAltoToValidPrimaryPageXml() throws IOException {
        byte[] pageXml = AltoToPageXml.convert(fixture("pixel.xml")).pageXml();

        assertThat(validation.validatePageXml(new ByteArrayResource(pageXml)).valid()).isTrue();
        assertThat(PageXml.create().version(pageXml)).isEqualTo(PageXmlConversionService.PRIMARY_VERSION);
        String xml = new String(pageXml, StandardCharsets.UTF_8);
        assertThat(xml).contains("imageFilename=\"0001.png\"", "imageWidth=\"1000\"", "imageHeight=\"1400\"");
        assertThat(xml).contains("TextRegion", "TextLine", "Imported", "ALTO", "Second");
    }

    @Test
    void convertedPageXmlOpensInTheEditorModel() throws IOException {
        byte[] pageXml = AltoToPageXml.convert(fixture("pixel.xml")).pageXml();

        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(pageXml);

        assertThat(page.imageWidth()).isEqualTo(1000);
        assertThat(page.imageHeight()).isEqualTo(1400);
        assertThat(page.getRegionCount()).isEqualTo(1);
        assertThat(page.getTextLineCount()).isEqualTo(2);
        assertThat(page.regions().getFirst().textLines().getFirst().words()).hasSize(3);
    }

    @Test
    void convertedPageXmlRecordsTheImportAndWhatWasNotKept() throws IOException {
        var converted = AltoToPageXml.convert(fixture("pixel.xml"));

        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(converted.pageXml());

        assertThat(converted.messages()).anySatisfy(message -> assertThat(message).contains("ComposedBlock"));
        assertThat(page.metadata().items())
                .filteredOn(item -> AltoToPageXml.IMPORT_STEP_NAME.equals(item.name()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.type()).isEqualTo("processingStep");
                    assertThat(item.date()).isNotBlank();
                    assertThat(item.labels().getFirst().labels())
                            .extracting(label -> label.value())
                            .containsExactlyElementsOf(converted.messages());
                });
    }

    @Test
    void rejectsAltoInPhysicalUnits() {
        assertThatThrownBy(() -> AltoToPageXml.convert(fixture("mm10.xml")))
                .isInstanceOfSatisfying(InvalidPageXmlException.class, error ->
                        assertThat(error.getValidation().errors())
                                .extracting(e -> e.code())
                                .containsExactly("ALTO_UNSUPPORTED_UNIT"));
    }

    @Test
    void importValidationAcceptsPixelAltoAndPageXmlOnly() throws IOException {
        assertThatCode(() -> validation.assertImportableXml(new ByteArrayResource(fixture("pixel.xml"))))
                .doesNotThrowAnyException();
        assertThatCode(() -> validation.assertImportableXml(
                new ByteArrayResource(primaryPageXml().getBytes(StandardCharsets.UTF_8))))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validation.assertImportableXml(new ByteArrayResource(fixture("mm10.xml"))))
                .isInstanceOf(InvalidPageXmlException.class);
        assertThatThrownBy(() -> validation.assertImportableXml(
                new ByteArrayResource("<root/>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(InvalidPageXmlException.class);
    }

    @Test
    void convertsAFileInPlace() throws IOException {
        Path xmlPath = tempDir.resolve("0001.xml");
        Files.write(xmlPath, fixture("pixel.xml"));

        AltoToPageXml.convertInPlace(xmlPath);

        assertThat(AltoToPageXml.isAlto(xmlPath)).isFalse();
        assertThat(validation.validatePageXml(new ByteArrayResource(Files.readAllBytes(xmlPath))).valid()).isTrue();
        assertThat(Files.list(tempDir)).containsExactly(xmlPath);
    }

    private static byte[] fixture(String name) throws IOException {
        return new ClassPathResource("alto/" + name).getContentAsByteArray();
    }

    static String primaryPageXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
                  <Metadata><Creator>test</Creator><Created>2026-01-01T00:00:00</Created><LastChange>2026-01-01T00:00:00</LastChange></Metadata>
                  <Page imageFilename="0001.png" imageWidth="10" imageHeight="10"/>
                </PcGts>
                """;
    }
}
