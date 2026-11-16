package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlAttributeScanner;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlAttributeScanner.AttributeOccurrence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageXmlAttributeScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scan_reportsEverySourceAttributeVerbatimWithoutSchemaDefaults() throws Exception {
        Path xmlPath = tempDir.resolve("page.xml");
        Files.writeString(xmlPath, """
                <?xml version="1.0" encoding="UTF-8"?>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15" xmlns:xlink="http://www.w3.org/1999/xlink">
                  <Metadata externalRef="ref-1">
                    <Creator>tester</Creator>
                    <Created>2026-03-04T10:00:00</Created>
                    <Comments>meta-comment</Comments>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1500" readingDirection="left-to-right" xlink:href="source.tif">
                    <ReadingOrder><OrderedGroup id="ro1" caption="logical"/></ReadingOrder>
                    <TextRegion id="r1" type="paragraph" continuation="false" comments="  keep spacing  ">
                      <Coords points="0,0 10,0 10,10 0,10"/>
                      <TextLine id="tl1" comments="duplicate">
                        <Coords points="0,0 10,0 10,10 0,10"/>
                        <TextEquiv>
                          <Unicode>without confidence</Unicode>
                        </TextEquiv>
                        <TextEquiv conf="0.91">
                          <Unicode>with confidence</Unicode>
                        </TextEquiv>
                      </TextLine>
                      <TextLine id="tl2" comments="duplicate"><Coords points="1,1 2,1 2,2"/></TextLine>
                    </TextRegion>
                  </Page>
                </PcGts>
                """);

        Set<AttributeOccurrence> attributes = PageXmlAttributeScanner.scan(xmlPath);
        assertTrue(attributes.contains(new AttributeOccurrence("Page", "imageFilename", "img.png")));
        assertTrue(attributes.contains(new AttributeOccurrence("Page", "href", "source.tif")));
        assertTrue(attributes.contains(new AttributeOccurrence("TextRegion", "comments", "  keep spacing  ")));
        assertTrue(attributes.contains(new AttributeOccurrence("Coords", "points", "0,0 10,0 10,10 0,10")));
        assertTrue(attributes.contains(new AttributeOccurrence("TextEquiv", "conf", "0.91")));
        assertTrue(attributes.contains(new AttributeOccurrence("OrderedGroup", "caption", "logical")));
        assertEquals(1, attributes.stream()
            .filter(attribute -> attribute.equals(new AttributeOccurrence("TextLine", "comments", "duplicate")))
            .count());
        assertFalse(attributes.contains(new AttributeOccurrence("Page", "orientation", "0")));
    }
}
