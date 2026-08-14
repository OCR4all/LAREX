package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlPresenceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageXmlPresenceIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void fromPath_tracksSparsePresenceWithoutMaterializingDefaults() throws Exception {
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

        PageXmlPresenceIndex index = PageXmlPresenceIndex.fromPath(xmlPath);

        assertTrue(index.hasMetadataCreator());
        assertTrue(index.hasMetadataCreated());
        assertTrue(index.hasMetadataComments());
        assertFalse(index.hasMetadataLastChange());
        assertTrue(index.hasMetadataExternalRef());
        assertTrue(index.hasPageAttribute("readingDirection"));
        assertTrue(index.hasTextRegionType("r1"));
        assertTrue(index.hasAttributeForElementId("r1", "continuation"));
        assertFalse(index.hasTextEquivConfidenceForElementId("tl1", 0));
        assertTrue(index.hasTextEquivConfidenceForElementId("tl1", 1));

        Set<PageXmlPresenceIndex.AttributeOccurrence> attributes = index.attributeOccurrences();
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("Page", "imageFilename", "img.png")));
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("Page", "href", "source.tif")));
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("TextRegion", "comments", "  keep spacing  ")));
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("Coords", "points", "0,0 10,0 10,10 0,10")));
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("TextEquiv", "conf", "0.91")));
        assertTrue(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("OrderedGroup", "caption", "logical")));
        assertEquals(1, attributes.stream()
            .filter(attribute -> attribute.equals(new PageXmlPresenceIndex.AttributeOccurrence("TextLine", "comments", "duplicate")))
            .count());
        assertFalse(attributes.contains(new PageXmlPresenceIndex.AttributeOccurrence("Page", "orientation", "0")));
    }
}
