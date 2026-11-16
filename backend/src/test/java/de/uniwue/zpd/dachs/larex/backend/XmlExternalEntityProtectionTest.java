package de.uniwue.zpd.dachs.larex.backend;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

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

        PageDto dto;
        try {
            dto = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(xml, null);
        } catch (IOException e) {
            assertThat(e).hasStackTraceContaining("PAGE XML").hasMessageNotContaining("SECRET-CANARY");
            return;
        }
        assertThat(dto.regions()).allSatisfy(region -> assertThat(String.valueOf(region.getText())).doesNotContain("SECRET-CANARY"));
    }
}
