package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageXmlValidationServiceTest {

    private final PageXmlValidationService service = new PageXmlValidationService();

    @Test
    void validatePageXml_acceptsAllDiscoveredSchemaVersions() {
        assertTrue(service.getSupportedVersions().contains("2019-07-15"));
        assertTrue(service.getSupportedVersions().contains("2024-07-15"));

        for (String version : service.getSupportedVersions()) {
            PageXmlTextDto.XmlValidationResult result = service.validatePageXml(validXml(version));
            assertTrue(result.valid(), "Expected valid XML for discovered version " + version);
            assertEquals(version, result.pageVersion());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 131_072})
    void validatePageXml_acceptsLeadingCommentContainingElement(int padding) {
        String xml = validXml("2019-07-15")
                .replace("<PcGts", "<!-- <Example/>" + " ".repeat(padding) + " -->\n<PcGts");

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);

        assertTrue(result.valid(), () -> result.errors().toString());
        assertEquals("2019-07-15", result.pageVersion());
        assertEquals("http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15", result.namespace());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16", "UTF-16LE", "UTF-16BE"})
    void validatePageXml_acceptsUtf16Resource(String encoding) {
        String xml = validXml("2019-07-15").replace("UTF-8", encoding);

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(
                new ByteArrayResource(xml.getBytes(Charset.forName(encoding))));

        assertTrue(result.valid(), () -> result.errors().toString());
        assertEquals("2019-07-15", result.pageVersion());
    }

    @Test
    void validatePageXml_acceptsPrefixedPageNamespace() {
        String namespace = "http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15";
        String xml = validXml("2019-07-15")
                .replace("<PcGts ", "<page:PcGts xmlns:page=\"" + namespace + "\" ")
                .replace("</PcGts>", "</page:PcGts>");

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);

        assertTrue(result.valid(), () -> result.errors().toString());
        assertEquals(namespace, result.namespace());
    }

    @Test
    void validatePageXml_detectsVersionFromSchemaLocationWithArbitraryPrefix() {
        String xml = """
                <PcGts xmlns:schema="http://www.w3.org/2001/XMLSchema-instance"
                       schema:schemaLocation="urn:page pagecontent-2019-07-15.xsd"/>
                """;

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);

        assertFalse(result.valid());
        assertEquals("2019-07-15", result.pageVersion());
        assertEquals("XSD_VALIDATION_ERROR", result.errors().getFirst().code());
    }

    @Test
    void validatePageXml_reportsUnreadableResource() throws IOException {
        Resource resource = mock(Resource.class);
        when(resource.getInputStream()).thenThrow(new IOException("Cannot open resource"));

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(resource);

        assertFalse(result.valid());
        assertEquals("XML_READ_ERROR", result.errors().getFirst().code());
    }

    @Test
    void validatePageXml_rejectsDifferentRootEvenWhenItContainsPcGts() {
        String xml = "<Other>" + validXml("2019-07-15")
                .replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "") + "</Other>";

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);

        assertFalse(result.valid());
        assertEquals("UNSUPPORTED_ROOT_ELEMENT", result.errors().getFirst().code());
    }

    @Test
    void validatePageXml_rejectsDoctype() {
        String xml = validXml("2019-07-15")
                .replace("<PcGts", "<!DOCTYPE PcGts [<!ENTITY content \"test\">]>\n<PcGts");

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);

        assertFalse(result.valid());
        assertEquals("XML_PARSE_ERROR", result.errors().getFirst().code());
    }

    @Test
    void validatePageXml_rejectsTranskribusLikeExtraElement() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000"/>
                  <TranskribusMetadata/>
                </PcGts>
                """;

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().stream().anyMatch(err -> "XSD_VALIDATION_ERROR".equals(err.code())));
        assertTrue(result.errors().stream().allMatch(err -> err.line() > 0 && err.column() > 0));
    }

    @Test
    void validatePageXml_rejectsMalformedXmlWithLocation() {
        String malformed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000">
                </PcGts>
                """;

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(malformed);
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
        assertEquals("XML_PARSE_ERROR", result.errors().getFirst().code());
        assertTrue(result.errors().getFirst().line() > 0);
        assertTrue(result.errors().getFirst().column() > 0);
    }

    @Test
    void validatePageXml_rejectsUnsupportedVersion() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2099-01-01">
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000"/>
                </PcGts>
                """;

        PageXmlTextDto.XmlValidationResult result = service.validatePageXml(xml);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(err -> "UNSUPPORTED_PAGE_VERSION".equals(err.code())));
    }

    private String validXml(String version) {
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
