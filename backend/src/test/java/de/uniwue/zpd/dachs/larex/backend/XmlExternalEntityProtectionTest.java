package de.uniwue.zpd.dachs.larex.backend;

import com.maxnth.page4j.dla.page.io.FileInput;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.io.xml.XmlPageReadResult;
import com.maxnth.page4j.dla.page.layout.physical.text.TextObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards against a page4j version whose PAGE XML reader resolves external entities (XXE). */
class XmlExternalEntityProtectionTest {

    @Test
    void pageXmlParsingCannotReadLocalFiles(@TempDir Path tempDir) throws Exception {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "SECRET-CANARY");
        Path xml = Files.writeString(tempDir.resolve("page.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE PcGts [<!ENTITY xxe SYSTEM "%s">]>
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
                  <Metadata><Creator>x</Creator><Created>2020-01-01T00:00:00</Created><LastChange>2020-01-01T00:00:00</LastChange></Metadata>
                  <Page imageFilename="a.png" imageWidth="10" imageHeight="10">
                    <TextRegion id="r1"><Coords points="0,0 5,0 5,5 0,5"/><TextEquiv><Unicode>&xxe;</Unicode></TextEquiv></TextRegion>
                  </Page>
                </PcGts>
                """.formatted(secret.toUri()));

        XmlPageReadResult result = PageXmlInputOutput.getReader().readWithSourceMetadata(new FileInput(xml.toFile()));

        if (result != null) {
            var layout = result.page().getLayout();
            for (int index = 0; index < layout.getRegionCount(); index++) {
                if (layout.getRegion(index) instanceof TextObject text) {
                    // page4j skips the unresolved entity, which can leave the text empty (null).
                    assertThat(Objects.toString(text.getText(), "")).doesNotContain("SECRET-CANARY");
                }
            }
        }
    }
}
