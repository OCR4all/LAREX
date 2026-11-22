package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.formats.SourceImage;
import com.maxnth.page4j.pagexml.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class OcrFormatToPageXmlTest {

    private final PageXmlValidationService validation = new PageXmlValidationService();

    @TempDir
    Path tempDir;

    @Test
    void recognisesAltoFineReaderAndHocrButNotPageXml() throws IOException {
        assertThat(OcrFormatToPageXml.sourceFormat(fixture("pixel.xml"))).contains(XmlSchema.ALTO_XML);
        assertThat(OcrFormatToPageXml.sourceFormat(fineReader())).contains(XmlSchema.FINE_READER_XML);
        assertThat(OcrFormatToPageXml.sourceFormat(hocr())).contains(XmlSchema.HOCR);
        assertThat(OcrFormatToPageXml.isConvertible(primaryPageXml().getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(OcrFormatToPageXml.isConvertible("not xml".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void convertsPixelAltoToValidPrimaryPageXml() throws IOException {
        byte[] pageXml = OcrFormatToPageXml.convert(fixture("pixel.xml")).pageXml();

        assertThat(validation.validatePageXml(new ByteArrayResource(pageXml)).valid()).isTrue();
        assertThat(PageXml.create().version(pageXml)).isEqualTo(PageXmlConversionService.PRIMARY_VERSION);
        String xml = new String(pageXml, StandardCharsets.UTF_8);
        assertThat(xml).contains("imageFilename=\"0001.png\"", "imageWidth=\"1000\"", "imageHeight=\"1400\"");
        assertThat(xml).contains("TextRegion", "TextLine", "Imported", "ALTO", "Second");
    }

    @Test
    void convertedPageXmlOpensInTheEditorModel() throws IOException {
        byte[] pageXml = OcrFormatToPageXml.convert(fixture("pixel.xml")).pageXml();

        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(pageXml);

        assertThat(page.imageWidth()).isEqualTo(1000);
        assertThat(page.imageHeight()).isEqualTo(1400);
        assertThat(page.getRegionCount()).isEqualTo(1);
        assertThat(page.getTextLineCount()).isEqualTo(2);
        assertThat(page.regions().getFirst().textLines().getFirst().words()).hasSize(3);
    }

    @Test
    void convertedPageXmlRecordsTheImportAndWhatWasNotKept() throws IOException {
        var converted = OcrFormatToPageXml.convert(fixture("pixel.xml"));

        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(converted.pageXml());

        assertThat(converted.messages()).anySatisfy(message -> assertThat(message).contains("ComposedBlock"));
        assertThat(page.metadata().items())
                .filteredOn(item -> OcrFormatToPageXml.importStepName(XmlSchema.ALTO_XML).equals(item.name()))
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
    void scalesPhysicalUnitsToThePageImage() throws IOException {
        SourceImage image = SourceImages.read(fixturePath("page-300dpi.png")).orElseThrow();

        byte[] pageXml = OcrFormatToPageXml.convert(fixture("mm10.xml"), imageFilename -> Optional.of(image)).pageXml();

        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(pageXml);
        assertThat(page.imageWidth()).isEqualTo(1181);
        assertThat(page.imageHeight()).isEqualTo(1654);
        assertThat(new String(pageXml, StandardCharsets.UTF_8)).contains("118,142");
    }

    @Test
    void readsThePageImageSizeAndResolution() {
        assertThat(SourceImages.read(fixturePath("page-300dpi.png"))).hasValueSatisfying(image -> {
            assertThat(image.width()).isEqualTo(1181);
            assertThat(image.height()).isEqualTo(1654);
            assertThat(image.xResolution()).isCloseTo(300, within(0.1));
            assertThat(image.yResolution()).isCloseTo(300, within(0.1));
        });
    }

    @Test
    void rejectsPhysicalUnitsWithoutAPageImage() {
        assertThatThrownBy(() -> OcrFormatToPageXml.convert(fixture("mm10.xml")))
                .isInstanceOfSatisfying(InvalidPageXmlException.class, error -> {
                    assertThat(error.getValidation().errors())
                            .extracting(e -> e.code())
                            .containsExactly("ALTO_IMAGE_REQUIRED");
                    assertThat(error.getMessage()).isEqualTo("Invalid ALTO XML: Coordinates in mm10 can only be converted "
                            + "to pixels with the page image. Upload the image together with the ALTO file.");
                });
    }

    @Test
    void importValidationAcceptsAltoItCanPlaceOnThePageImage() throws IOException {
        assertThatCode(() -> importable(fixture("pixel.xml"))).doesNotThrowAnyException();
        assertThatCode(() -> importable(primaryPageXml().getBytes(StandardCharsets.UTF_8))).doesNotThrowAnyException();
        assertThatThrownBy(() -> importable(fixture("mm10.xml"))).isInstanceOf(InvalidPageXmlException.class);
        assertThat(validation.toImportablePageXml(new ByteArrayResource(fixture("mm10.xml")),
                imageFilename -> SourceImages.read(fixturePath("page-300dpi.png"))))
                .isPresent();
        assertThatThrownBy(() -> importable("<root/>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidPageXmlException.class);
    }

    @Test
    void importCheckReturnsThePageXmlToStoreForAltoOnly() throws IOException {
        var forAlto = importable(fixture("pixel.xml"));
        var forPage = importable(primaryPageXml().getBytes(StandardCharsets.UTF_8));

        assertThat(forAlto).hasValueSatisfying(pageXml -> {
            assertThat(OcrFormatToPageXml.isConvertible(pageXml)).isFalse();
            assertThat(validation.validatePageXml(new ByteArrayResource(pageXml)).valid()).isTrue();
        });
        assertThat(forPage).isEmpty();
    }

    @Test
    void convertsAFileInPlace() throws IOException {
        Path xmlPath = tempDir.resolve("0001.xml");
        Files.write(xmlPath, fixture("pixel.xml"));

        OcrFormatToPageXml.convertInPlace(xmlPath, imageFilename -> Optional.empty());

        assertThat(OcrFormatToPageXml.sourceFormat(xmlPath).isPresent()).isFalse();
        assertThat(validation.validatePageXml(new ByteArrayResource(Files.readAllBytes(xmlPath))).valid()).isTrue();
        assertThat(Files.list(tempDir)).containsExactly(xmlPath);
    }

    @Test
    void convertsFineReaderToValidPageXmlWithWordsFromCharacters() throws IOException {
        var converted = OcrFormatToPageXml.convert(fineReader());

        assertThat(converted.source()).isEqualTo(XmlSchema.FINE_READER_XML);
        assertThat(validation.validatePageXml(new ByteArrayResource(converted.pageXml())).valid()).isTrue();
        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(converted.pageXml());
        assertThat(page.imageWidth()).isEqualTo(1000);
        assertThat(page.imageHeight()).isEqualTo(1400);
        assertThat(page.getRegionCount()).isEqualTo(2);
        assertThat(page.getTextLineCount()).isEqualTo(2);
        assertThat(page.regions().getFirst().textLines().getFirst().words())
                .extracting(word -> word.getText())
                .containsExactly("Gut", "Tag");
        assertThat(page.metadata().items())
                .extracting(item -> item.name())
                .contains("LAREX FineReader XML import");
    }

    @Test
    void convertsHocrToValidPageXml() throws IOException {
        var converted = OcrFormatToPageXml.convert(hocr());

        assertThat(converted.source()).isEqualTo(XmlSchema.HOCR);
        assertThat(validation.validatePageXml(new ByteArrayResource(converted.pageXml())).valid()).isTrue();
        String xml = new String(converted.pageXml(), StandardCharsets.UTF_8);
        assertThat(xml).contains("imageFilename=\"0001.png\"", "Zweite", "Zeile");
        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(converted.pageXml());
        assertThat(page.getTextLineCount()).isEqualTo(2);
        assertThat(page.regions().getFirst().textLines().getFirst().words()).hasSize(2);
        assertThat(page.metadata().items())
                .extracting(item -> item.name())
                .contains("LAREX hOCR import");
    }

    @Test
    void reportsUnreadableHocrAsInvalidHocr() {
        byte[] broken = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><div class=\"ocr_page\">"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> OcrFormatToPageXml.convert(broken))
                .isInstanceOfSatisfying(InvalidPageXmlException.class, error -> {
                    assertThat(error.title()).isEqualTo("Invalid hOCR");
                    assertThat(error.getValidation().errors()).extracting(e -> e.code()).containsExactly("HOCR_READ_ERROR");
                });
    }

    @Test
    void namesConvertedFilesAsXml() {
        assertThat(OcrFormatToPageXml.pageXmlFileName("0001.hocr")).isEqualTo("0001.xml");
        assertThat(OcrFormatToPageXml.pageXmlFileName("0001.html")).isEqualTo("0001.xml");
        assertThat(OcrFormatToPageXml.pageXmlFileName("0001.xml")).isEqualTo("0001.xml");
        assertThat(OcrFormatToPageXml.pageXmlFileName("0001.png")).isEqualTo("0001.png");
        assertThat(OcrFormatToPageXml.hasSupportedExtension("Page.XHTML")).isTrue();
        assertThat(OcrFormatToPageXml.hasSupportedExtension("page.txt")).isFalse();
    }

    private static byte[] fineReader() throws IOException {
        return new ClassPathResource("finereader/0001.xml").getContentAsByteArray();
    }

    private static byte[] hocr() throws IOException {
        return new ClassPathResource("hocr/0001.hocr").getContentAsByteArray();
    }

    private Optional<byte[]> importable(byte[] xml) {
        return validation.toImportablePageXml(new ByteArrayResource(xml), imageFilename -> Optional.empty());
    }

    private static Path fixturePath(String name) {
        try {
            return new ClassPathResource("alto/" + name).getFile().toPath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
