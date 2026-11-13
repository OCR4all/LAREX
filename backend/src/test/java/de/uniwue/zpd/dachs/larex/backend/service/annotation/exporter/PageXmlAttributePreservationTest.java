package de.uniwue.zpd.dachs.larex.backend.service.annotation.exporter;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PageXmlAttributePreservationTest {

    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());

    @TempDir
    Path directory;

    @Test
    void preservesBgColourCoordsConfAndReadingOrderConfThroughSave() throws Exception {
        Path source = directory.resolve("source.xml");
        Files.writeString(source, """
            <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
              <Metadata><Creator>tester</Creator><Created>2026-11-13T10:00:00</Created><LastChange>2026-11-13T10:00:00</LastChange></Metadata>
              <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                <ReadingOrder conf="0.42"><OrderedGroup id="root">
                  <RegionRefIndexed regionRef="image" index="0"/>
                </OrderedGroup></ReadingOrder>
                <ImageRegion id="image" bgColour="yellow"><Coords points="0,0 10,0 10,10" conf="0.7"/></ImageRegion>
                <TableRegion id="table" bgColour="green"><Coords points="0,20 10,20 10,30"/></TableRegion>
                <SeparatorRegion id="separator"><Coords points="0,40 10,40 10,50"/></SeparatorRegion>
              </Page>
            </PcGts>
            """);

        PageDto parsed = parser.parse(source, null);
        Path saved = directory.resolve("saved.xml");
        Files.writeString(saved, exporter.export(parsed, null));
        PageDto reparsed = parser.parse(saved, null);

        assertEquals("yellow", region(reparsed, "image").bgColour());
        assertEquals(0.7, region(reparsed, "image").coords().confidence(), 1e-6);
        assertEquals("green", region(reparsed, "table").bgColour());
        assertNull(region(reparsed, "separator").bgColour());
        assertEquals(0.42, reparsed.readingOrder().confidence(), 1e-6);
    }

    private RegionDto region(PageDto page, String id) {
        return page.regions().stream().filter(region -> id.equals(region.id())).findFirst().orElseThrow();
    }
}
