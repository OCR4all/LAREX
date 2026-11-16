package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.ReadingOrderDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DtoToPage4jMapperReferenceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SOURCE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
              <Metadata><Creator>c</Creator><Created>2024-01-02T03:04:05</Created><LastChange>2024-01-02T03:04:05</LastChange></Metadata>
              <Page imageFilename="a.png" imageWidth="100" imageHeight="100">
                <TextRegion id="r1">
                  <Coords points="1,1 50,1 50,50 1,50"/>
                  <TextLine id="l1"><Coords points="1,1 50,1 50,10 1,10"/><TextEquiv><Unicode>t</Unicode></TextEquiv></TextLine>
                </TextRegion>
              </Page>
            </PcGts>
            """;

    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());
    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());

    @Test
    void groupLeftEmptyByADeletedRegionIsDroppedOnSave() throws Exception {
        PageDto saved = save(page -> {
            ArrayNode members = readingOrder(page, "r1");
            ObjectNode nested = members.addObject();
            nested.put("type", "nestedGroup");
            ObjectNode group = nested.putObject("group");
            group.put("id", "ro-side");
            group.put("ordered", false);
            regionRef(group.putArray("members"), "deleted");
        });

        List<ReadingOrderDto.GroupMemberDto> members = saved.readingOrder().root().members();
        assertEquals(1, members.size());
        assertEquals("r1", ((ReadingOrderDto.RegionRefDto) members.getFirst()).regionRef());
    }

    @Test
    void readingOrderLeftEmptyIsDroppedOnSave() throws Exception {
        PageDto saved = save(page -> readingOrder(page, "deleted"));

        assertNull(saved.readingOrder());
    }

    @Test
    void idThatIsNotAnNcNameIsRepairedAndReferencesFollowIt() throws Exception {
        PageDto saved = save(page -> {
            ((ObjectNode) page.get("regions").get(0)).put("id", "a/b");
            readingOrder(page, "a/b");
            ObjectNode relation = page.putObject("relations").putArray("relations").addObject();
            relation.put("id", "rel1");
            relation.put("type", "link");
            relation.put("sourceRegionRef", "a/b");
            relation.put("targetRegionRef", "l1");
        });

        String regionId = saved.regions().getFirst().id();
        assertEquals("r1", regionId);
        assertEquals(regionId, ((ReadingOrderDto.RegionRefDto) saved.readingOrder().root().members().getFirst()).regionRef());
        assertEquals(regionId, saved.relations().relations().getFirst().sourceRegionRef());
    }

    private PageDto save(Consumer<ObjectNode> edit) throws Exception {
        ObjectNode page = (ObjectNode) JSON.readTree(JSON.writeValueAsString(parser.parse(SOURCE.getBytes(StandardCharsets.UTF_8))));
        edit.accept(page);
        String xml = exporter.exportToString(JSON.treeToValue(page, PageDto.class), (de.uniwue.zpd.dachs.larex.backend.entity.PageXml) null);
        return parser.parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static ArrayNode readingOrder(ObjectNode page, String ref) {
        ObjectNode root = page.putObject("readingOrder").putObject("root");
        root.put("id", "ro-root");
        root.put("ordered", true);
        ArrayNode members = root.putArray("members");
        regionRef(members, ref);
        return members;
    }

    private static void regionRef(ArrayNode members, String ref) {
        ObjectNode member = members.addObject();
        member.put("type", "regionRef");
        member.put("regionRef", ref);
    }
}
