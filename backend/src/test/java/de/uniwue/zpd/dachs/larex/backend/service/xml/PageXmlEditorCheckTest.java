package de.uniwue.zpd.dachs.larex.backend.service.xml;

import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import com.maxnth.page4j.pagexml.PageReadException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageXmlEditorCheckTest {

    private final PageXmlValidationService validationService = new PageXmlValidationService();
    private final PageXmlEditorCheck check = new PageXmlEditorCheck(
            validationService,
            new PageXmlToAnnotationParser(new Page4jToDtoMapper())
    );

    @Test
    void acceptsAPageTheEditorCanOpen() {
        PageXmlTextDto.XmlValidationResult result = check.validate(page("", ""));

        assertTrue(result.valid());
        assertEquals("2024-07-15", result.pageVersion());
    }

    @Test
    void keepsSchemaErrorsAsTheyAre() {
        PageXmlTextDto.XmlValidationResult result = check.validate(page("", "").replace("<TextRegion id=\"r1\">", "<TextRegion id=\"r1\" foo=\"bar\">"));

        assertFalse(result.valid());
        assertEquals("XSD_VALIDATION_ERROR", result.errors().getFirst().code());
    }

    @Test
    void reportsEachReadErrorAtItsOwnPosition() throws Exception {
        PageXmlToAnnotationParser parser = mock(PageXmlToAnnotationParser.class);
        PageReadException read = new PageReadException(4, 7, List.of(
                "4:7: Unresolved reference 'x' in Group 'g'",
                "9: Duplicate ID 'r1'",
                "Cannot build the page"
        ), null);
        when(parser.parse(any(byte[].class))).thenThrow(new IOException("Failed to parse PAGE XML file", read));

        PageXmlTextDto.XmlValidationResult result = new PageXmlEditorCheck(validationService, parser).validate(page("", ""));

        assertFalse(result.valid());
        assertEquals("2024-07-15", result.pageVersion());
        assertEquals(List.of(
                new PageXmlTextDto.XmlValidationError(4, 7, "error", PageXmlEditorCheck.READ_ERROR, "Unresolved reference 'x' in Group 'g'"),
                new PageXmlTextDto.XmlValidationError(9, 1, "error", PageXmlEditorCheck.READ_ERROR, "Duplicate ID 'r1'"),
                new PageXmlTextDto.XmlValidationError(4, 7, "error", PageXmlEditorCheck.READ_ERROR, "Cannot build the page")
        ), result.errors());
    }

    @Test
    void reportsWhatTheEditorCannotShow() throws Exception {
        PageXmlToAnnotationParser parser = mock(PageXmlToAnnotationParser.class);
        when(parser.parse(any(byte[].class))).thenThrow(new IllegalArgumentException("FrameRegion 'f1' is not supported by the editor yet"));

        PageXmlTextDto.XmlValidationResult result = new PageXmlEditorCheck(validationService, parser).validate(page("", ""));

        assertFalse(result.valid());
        assertEquals(List.of(new PageXmlTextDto.XmlValidationError(
                1, 1, "error", PageXmlEditorCheck.EDITOR_ERROR, "FrameRegion 'f1' is not supported by the editor yet")), result.errors());
    }

    private static String page(String pcGtsAttributes, String beforeRegions) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15"%s>
                  <Metadata>
                    <Creator>tester</Creator>
                    <Created>2026-03-05T10:00:00</Created>
                    <LastChange>2026-03-05T10:00:00</LastChange>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1000">
                %s    <TextRegion id="r1">
                      <Coords points="10,10 100,10 100,100 10,100"/>
                    </TextRegion>
                  </Page>
                </PcGts>
                """.formatted(pcGtsAttributes, beforeRegions);
    }
}
