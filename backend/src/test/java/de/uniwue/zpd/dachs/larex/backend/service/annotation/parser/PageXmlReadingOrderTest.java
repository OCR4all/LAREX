package de.uniwue.zpd.dachs.larex.backend.service.annotation.parser;

import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageXmlReadingOrderTest {
    @TempDir
    Path directory;

    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());

    @Test
    void keepsUnorderedGroupMembersInSourceOrder() throws Exception {
        Path path = directory.resolve("unordered.xml");
        Files.writeString(path, """
            <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
              <Metadata><Creator>tester</Creator><Created>2026-11-06T10:00:00</Created><LastChange>2026-11-06T10:00:00</LastChange></Metadata>
              <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                <ReadingOrder><UnorderedGroup id="root">
                  <RegionRef regionRef="second"/>
                  <OrderedGroup id="nested">
                    <RegionRefIndexed regionRef="first" index="1"/>
                    <RegionRefIndexed regionRef="third" index="0"/>
                  </OrderedGroup>
                </UnorderedGroup></ReadingOrder>
                <TextRegion id="first"><Coords points="0,0 10,0 10,10"/></TextRegion>
                <TextRegion id="second"><Coords points="0,20 10,20 10,30"/></TextRegion>
                <TextRegion id="third"><Coords points="0,40 10,40 10,50"/></TextRegion>
              </Page>
            </PcGts>
            """);
        assertEquals(List.of("second", "third", "first"), regionIds(parser.parse(path, null).readingOrder().root()));
    }

    @Test
    void preservesIndexedReadingOrderThroughImportAndSave() throws Exception {
        Path path = directory.resolve("reading-order.xml");
        Files.writeString(path, """
            <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2019-07-15">
              <Metadata><Creator>tester</Creator><Created>2026-11-06T10:00:00</Created><LastChange>2026-11-06T10:00:00</LastChange></Metadata>
              <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                <ReadingOrder>
                  <OrderedGroup id="root">
                    <RegionRefIndexed regionRef="last" index="2"/>
                    <OrderedGroupIndexed id="nested" index="0">
                      <RegionRefIndexed regionRef="second" index="1"/>
                      <RegionRefIndexed regionRef="first" index="0"/>
                    </OrderedGroupIndexed>
                    <RegionRefIndexed regionRef="middle" index="1"/>
                  </OrderedGroup>
                </ReadingOrder>
                <TextRegion id="last"><Coords points="0,0 10,0 10,10"/></TextRegion>
                <TextRegion id="middle"><Coords points="0,20 10,20 10,30"/></TextRegion>
                <TextRegion id="second"><Coords points="0,40 10,40 10,50"/></TextRegion>
                <TextRegion id="first"><Coords points="0,60 10,60 10,70"/></TextRegion>
              </Page>
            </PcGts>
            """);

        var page = parser.parse(path, null);
        assertEquals(List.of("first", "second", "middle", "last"), regionIds(page.readingOrder().root()));

        var exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
        Files.writeString(path, exporter.export(page, null));
        assertEquals(List.of("first", "second", "middle", "last"),
            regionIds(parser.parse(path, null).readingOrder().root()));
    }

    private List<String> regionIds(ReadingOrderDto.GroupDto group) {
        List<String> ids = new ArrayList<>();
        for (var member : group.members()) {
            if (member instanceof ReadingOrderDto.RegionRefDto ref) ids.add(ref.regionRef());
            else if (member instanceof ReadingOrderDto.NestedGroupDto nested) ids.addAll(regionIds(nested.group()));
        }
        return ids;
    }
}
