package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToAltoXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AltoTableExportTest {

    @TempDir
    Path directory;

    @Test
    void flattensATableIntoAComposedBlockOfCellTextBlocks() throws Exception {
        Path source = directory.resolve("table.xml");
        Files.writeString(source, """
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
                  <Metadata><Creator>tester</Creator><Created>2026-11-20T10:00:00</Created><LastChange>2026-11-20T10:00:00</LastChange></Metadata>
                  <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                    <TableRegion id="t" rows="1" columns="2">
                      <Coords points="0,0 200,0 200,100 0,100"/>
                      <TextRegion id="a"><Coords points="0,0 100,0 100,100 0,100"/>
                        <Roles><TableCellRole rowIndex="0" columnIndex="0"/></Roles>
                        <TextLine id="la"><Coords points="5,5 95,5 95,40 5,40"/><TextEquiv><Unicode>left</Unicode></TextEquiv></TextLine>
                      </TextRegion>
                      <TextRegion id="b"><Coords points="100,0 200,0 200,100 100,100"/>
                        <Roles><TableCellRole rowIndex="0" columnIndex="1"/></Roles>
                        <TextLine id="lb"><Coords points="105,5 195,5 195,40 105,40"/><TextEquiv><Unicode>right</Unicode></TextEquiv></TextLine>
                      </TextRegion>
                      <Grid>
                        <GridPoints index="0" points="0,0 100,0 200,0"/>
                        <GridPoints index="1" points="0,100 100,100 200,100"/>
                      </Grid>
                    </TableRegion>
                  </Page>
                </PcGts>
                """);
        PageDto page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(source, null);

        String alto = new AnnotationToAltoXmlExporter(new DtoToPage4jMapper()).toXmlString(page);

        Matcher table = Pattern.compile("<ComposedBlock[^>]*ID=\"t\"[^>]*>(.*?)</ComposedBlock>", Pattern.DOTALL).matcher(alto);
        assertTrue(table.find(), alto);
        assertTrue(table.group(0).contains("TYPE=\"TableRegion\""), table.group(0));
        String cells = table.group(1);
        assertTrue(cells.indexOf("<TextBlock ID=\"a\"") < cells.indexOf("<TextBlock ID=\"b\""), cells);
        assertTrue(cells.contains("CONTENT=\"left\"") && cells.contains("CONTENT=\"right\""), cells);
    }
}
