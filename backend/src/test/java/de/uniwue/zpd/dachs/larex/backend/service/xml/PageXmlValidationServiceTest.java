package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import org.junit.jupiter.api.Test;

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
