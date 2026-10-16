package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextContentVariantDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AnnotationTextContentRoundTripTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());

    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {
        "null",
        "[]",
        "[{}]",
        "[{\"unicode\":\"\"}]",
        "[{\"unicode\":\"\",\"index\":0},{\"unicode\":\"\",\"index\":1}]",
        "[{\"index\":2,\"confidence\":0.0,\"comments\":\"explicit variant\"}]",
        "[{\"unicode\":\"GT\",\"index\":0},{\"plainText\":\"OCR\",\"index\":1}]"
    })
    void preservesTextVariantPresenceThroughXmlAndFrontendJson(String variants) throws Exception {
        PageDto original = page(variants);
        PageDto current = original;
        int expectedCount = original.regions().getFirst().textContentVariants() == null
            ? 0 : original.regions().getFirst().textContentVariants().size();
        Path xmlPath = directory.resolve("page.xml");

        for (int save = 0; save < 2; save++) {
            String xml = exporter.export(current, null);
            assertEquals(4 * expectedCount, xml.split("<TextEquiv", -1).length - 1, xml);
            Files.writeString(xmlPath, xml);
            PageDto parsed = parser.parse(xmlPath, null);
            List<List<TextContentVariantDto>> owners = textVariants(parsed);
            for (List<TextContentVariantDto> actual : owners) {
                if (expectedCount == 0) {
                    assertNull(actual);
                } else {
                    assertEquals(expectedCount, actual.size());
                    List<TextContentVariantDto> expected = original.regions().getFirst().textContentVariants();
                    for (int i = 0; i < expectedCount; i++) {
                        assertEquals(expected.get(i).index(), actual.get(i).index());
                        assertEquals(expected.get(i).confidence(), actual.get(i).confidence());
                        assertEquals(expected.get(i).comments(), actual.get(i).comments());
                        assertEquals(expected.get(i).plainText(), actual.get(i).plainText());
                        String unicode = expected.get(i).unicode();
                        assertEquals("".equals(unicode) ? null : unicode, actual.get(i).unicode());
                    }
                }
            }
            current = json.readValue(json.writeValueAsString(parsed), PageDto.class);
        }
    }

    private List<List<TextContentVariantDto>> textVariants(PageDto page) {
        var region = page.regions().getFirst();
        var line = region.textLines().getFirst();
        var word = line.words().getFirst();
        var glyph = word.glyphs().getFirst();
        return Arrays.asList(region.textContentVariants(), line.textContentVariants(),
            word.textContentVariants(), glyph.textContentVariants());
    }

    private PageDto page(String variants) {
        String coords = "{\"points\":[[-0.5,-0.5],[0.5,-0.5],[0.5,0.5],[-0.5,0.5]]}";
        return json.readValue("""
            {
              "imageFilename": "page.png", "imageWidth": 1000, "imageHeight": 1500,
              "metadata": {"creator":"tester","created":"2026-10-16T10:00:00","lastChange":"2026-10-16T10:00:00"},
              "regions": [{
                "id":"r1", "kind":"TextRegion", "coords":$coords, "textContentVariants":$variants,
                "textLines":[{
                  "id":"l1", "coords":$coords, "textContentVariants":$variants,
                  "words":[{
                    "id":"w1", "coords":$coords, "textContentVariants":$variants,
                    "glyphs":[{"id":"g1", "coords":$coords, "textContentVariants":$variants}]
                  }]
                }]
              }]
            }
            """.replace("$coords", coords).replace("$variants", variants), PageDto.class);
    }
}
