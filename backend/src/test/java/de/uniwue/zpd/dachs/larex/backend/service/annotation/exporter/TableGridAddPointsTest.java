package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridAddPointsDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableGridAddPointsTest {

    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());

    @TempDir
    Path directory;

    @Test
    void addPointsUseTheSameCoordinatesAsTheGridAndSurviveSaving() throws Exception {
        PageDto parsed = parse(table("left=\"500,250\" top=\"250,100 300,90\""));
        GridAddPointsDto addPoints = parsed.regions().getFirst().grid().addPoints().getFirst();

        assertNull(addPoints.left());
        assertNull(addPoints.top());
        assertEquals(List.of(new PointDto(CoordinateUtils.pixelToWorldX(500, 1000), CoordinateUtils.pixelToWorldY(250, 1500))),
            addPoints.leftPoints().points());
        assertEquals(2, addPoints.topPoints().points().size());

        String saved = exporter.export(parsed, null);
        assertTrue(saved.contains("left=\"500,250\""), saved);
        assertTrue(saved.contains("top=\"250,100 300,90\""), saved);
    }

    @Test
    void keepsAddPointsTextThatIsNotAPointListVerbatim() throws Exception {
        PageDto parsed = parse(table("left=\"text\" top=\"1,2  3,4\""));
        GridAddPointsDto addPoints = parsed.regions().getFirst().grid().addPoints().getFirst();

        assertEquals("text", addPoints.left());
        assertEquals("1,2  3,4", addPoints.top());
        assertNull(addPoints.leftPoints());
        assertNull(addPoints.topPoints());

        String saved = exporter.export(parsed, null);
        assertTrue(saved.contains("left=\"text\""), saved);
        assertTrue(saved.contains("top=\"1,2  3,4\""), saved);
    }

    private PageDto parse(String xml) throws Exception {
        Path source = directory.resolve("source.xml");
        Files.writeString(source, xml);
        return parser.parse(source, null);
    }

    private static String table(String addPointsAttributes) {
        return """
            <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
              <Metadata><Creator>tester</Creator><Created>2026-11-19T10:00:00</Created><LastChange>2026-11-19T10:00:00</LastChange></Metadata>
              <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                <TableRegion id="table" rows="1" columns="1">
                  <Coords points="0,0 1000,0 1000,500 0,500"/>
                  <Grid>
                    <GridPoints index="0" points="0,0 1000,0"/>
                    <GridPoints index="1" points="0,500 1000,500"/>
                    <AddPoints row="0" col="1" %s/>
                  </Grid>
                </TableRegion>
              </Page>
            </PcGts>
            """.formatted(addPointsAttributes);
    }
}
