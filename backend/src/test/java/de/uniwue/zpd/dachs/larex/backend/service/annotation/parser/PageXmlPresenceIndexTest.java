package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlPresenceIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


import java.nio.file.Files;
import java.nio.file.Path;

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
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
                  <Metadata externalRef="ref-1">
                    <Creator>tester</Creator>
                    <Created>2026-03-04T10:00:00</Created>
                    <Comments>meta-comment</Comments>
                  </Metadata>
                  <Page imageFilename="img.png" imageWidth="1000" imageHeight="1500" readingDirection="left-to-right">
                    <TextRegion id="r1" type="paragraph" continuation="false">
                      <Coords points="0,0 10,0 10,10 0,10"/>
                      <TextLine id="tl1">
                        <Coords points="0,0 10,0 10,10 0,10"/>
                        <TextEquiv>
                          <Unicode>without confidence</Unicode>
                        </TextEquiv>
                        <TextEquiv conf="0.91">
                          <Unicode>with confidence</Unicode>
                        </TextEquiv>
                      </TextLine>
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
    }
}
