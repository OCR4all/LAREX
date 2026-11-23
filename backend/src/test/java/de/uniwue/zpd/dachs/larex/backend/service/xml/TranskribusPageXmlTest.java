package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.OrderedGroup;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.RegionRef;
import com.maxnth.page4j.model.TableCellRole;
import com.maxnth.page4j.model.TableRegion;
import com.maxnth.page4j.model.TextLine;
import com.maxnth.page4j.model.TextRegion;
import com.maxnth.page4j.pagexml.PageXml;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.exception.InvalidPageXmlException;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranskribusPageXmlTest {

    private final PageXmlValidationService validation = new PageXmlValidationService();

    @Test
    void recognisesTranskribusExportsByMetadataTableCellsOrCreator() throws IOException {
        assertThat(TranskribusPageXml.isExport(fixture("export.xml"))).isTrue();
        assertThat(TranskribusPageXml.isExport(fixture("table.xml"))).isTrue();
        assertThat(TranskribusPageXml.isExport(fixture("negative-coords.xml"))).isTrue();
        assertThat(TranskribusPageXml.isExport(page("tester", "<Coords points=\"-1,0 10,0 10,10\"/>")
                .getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(TranskribusPageXml.isExport(new ClassPathResource("alto/pixel.xml").getContentAsByteArray())).isFalse();
        assertThat(TranskribusPageXml.isExport("not xml".getBytes(StandardCharsets.UTF_8))).isFalse();
    }

    @Test
    void theRawExportsFailValidation() throws IOException {
        for (String name : List.of("export.xml", "table.xml", "negative-coords.xml")) {
            assertThat(validation.validatePageXml(new ByteArrayResource(fixture(name))).valid()).as(name).isFalse();
        }
    }

    @Test
    void movesTranskribusMetadataToAMetadataItemWithoutItsFileUrls() throws IOException {
        byte[] pageXml = importable("export.xml");
        Page page = PageXml.create().read(pageXml).page();

        String xml = new String(pageXml, StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<TranskribusMetadata", "files.transkribus.eu", "4242");
        MetadataItem transkribus = metadataItem(page, TranskribusPageXml.METADATA_ITEM_NAME);
        assertThat(transkribus.labels().getFirst().labels())
                .extracting(label -> label.type() + "=" + label.value())
                .containsExactly("docId=1012345", "pageId=45678901", "pageNr=1", "tsid=98765432",
                        "imageId=55501234", "status=IN_PROGRESS");
        assertThat(metadataItem(page, TranskribusPageXml.IMPORT_STEP_NAME).labels().getFirst().labels())
                .anySatisfy(label -> assertThat(label.value()).contains("TranskribusMetadata"));
        assertThat(page.metadata().creator()).contains("PyLaia@TranskribusPlatform");
    }

    @Test
    void keepsRegionsLinesTextAndReadingOrder() throws IOException {
        Page page = PageXml.create().read(importable("export.xml")).page();

        assertThat(page.allRegions()).extracting(region -> region.id()).containsExactly("r1", "r2");
        assertThat(page.textLines()).extracting(TextLine::id).containsExactly("r1l1", "r2l1", "r2l2");
        assertThat(page.textLines()).extracting(line -> line.textEquivs().getFirst().unicode())
                .containsExactly("Erstes Kapitel", "Anna schrieb einen Brief", "an ihre Schwester.");
        assertThat(page.textLines().get(1).custom()).isEqualTo("readingOrder {index:0;} person {offset:0; length:4;}");
        assertThat(((OrderedGroup) page.readingOrder().group()).members())
                .extracting(member -> ((RegionRef) member).regionRef())
                .containsExactly("r2", "r1");
    }

    @Test
    void convertsTableCellsToTextRegionsWithTableCellRoles() throws IOException {
        byte[] pageXml = importable("table.xml");
        Page page = PageXml.create().read(pageXml).page();

        String xml = new String(pageXml, StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<TableCell ", "</TableCell>", "<CornerPts", "BorderVisible=", "label=\"false\"");
        TableRegion table = page.find("t1", TableRegion.class).orElseThrow();
        assertThat(table.rows()).isEqualTo(2);
        assertThat(table.columns()).isEqualTo(2);
        assertThat(table.regions()).extracting(region -> region.id()).containsExactly("c1", "c2", "c3");
        assertThat(table.regions()).allSatisfy(region -> assertThat(region).isInstanceOf(TextRegion.class));
        assertThat(table.regions()).extracting(region -> region.tableCellRole()).containsExactly(
                new TableCellRole(0, 0, null, null, null),
                new TableCellRole(0, 1, null, null, null),
                new TableCellRole(1, 0, null, 2, null));
        assertThat(page.textLines()).extracting(line -> line.textEquivs().getFirst().unicode())
                .containsExactly("Name", "Ort", "Anna Weber, Würzburg");
    }

    @Test
    void tableCellsOpenAsTableCellsInTheEditorModel() throws IOException {
        var page = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(importable("table.xml"));

        RegionDto table = page.regions().getFirst();
        assertThat(table.nestedRegions()).extracting(cell -> cell.tableCellRoleFromRoles().colSpan())
                .containsExactly(null, null, 2);
        assertThat(table.nestedRegions()).extracting(cell -> cell.tableCellRoleFromRoles().rowIndex())
                .containsExactly(0, 0, 1);
    }

    @Test
    void dropsAttributesThePageSchemaDoesNotAllowOnTableRegions() {
        String xml = page("Transkribus", """
                <TableRegion id="t1" orientation="0.0" tableType="grid">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                </TableRegion>""");

        byte[] pageXml = validation.toImportablePageXml(resource(xml), image -> Optional.empty()).orElseThrow();

        assertThat(new String(pageXml, StandardCharsets.UTF_8)).doesNotContain("tableType=").contains("id=\"t1\"");
    }

    @Test
    void clampsNegativeAndOutOfBoundsPointsToThePageImage() throws IOException {
        Page page = PageXml.create().read(importable("negative-coords.xml")).page();

        TextRegion region = page.find("r1", TextRegion.class).orElseThrow();
        assertThat(points(region.coords())).isEqualTo("0,40 1000,40 1000,1400 0,1400");
        TextLine line = page.textLines().getFirst();
        assertThat(points(line.coords())).isEqualTo("0,50 600,50 600,120 0,120");
        assertThat(points(line.baseline())).isEqualTo("0,110 599,110");
    }

    @Test
    void roundsFractionalPointsAndLeavesUnreadablePointsAlone() {
        assertThat(TranskribusPageXml.normalizePoints("10.4,-0.6 99.5,20", 50, 50)).isEqualTo("10,0 50,20");
        assertThat(TranskribusPageXml.normalizePoints("1,2 3,4", 0, 0)).isEqualTo("1,2 3,4");
        assertThat(TranskribusPageXml.normalizePoints("1,2 oops", 10, 10)).isNull();
    }

    @Test
    void removesTranskribusTagsPropertiesLinksAndImageTransformAttributes() {
        String xml = page("Transkribus", """
                <TextRegion id="r1">
                  <Tag name="person"><Property key="firstname" value="Anna"/></Tag>
                  <Link ref="r1" type="sameAs"/>
                  <Coords points="0,0 100,0 100,100 0,100"/>
                </TextRegion>""").replace("imageHeight=\"200\"", "imageHeight=\"200\" imageRotation=\"90\"");

        byte[] pageXml = validation.toImportablePageXml(resource(xml), image -> Optional.empty()).orElseThrow();

        assertThat(new String(pageXml, StandardCharsets.UTF_8)).doesNotContain("<Tag", "<Property", "<Link", "imageRotation=");
    }

    @Test
    void leavesTranskribusExportsWithNothingToFixUnchanged() {
        String xml = page("Transkribus", "<TextRegion id=\"r1\"><Coords points=\"0,0 100,0 100,100 0,100\"/></TextRegion>");

        assertThat(validation.toImportablePageXml(resource(xml), image -> Optional.empty())).isEmpty();
    }

    @Test
    void doesNotRepairPageXmlFromOtherTools() {
        String xml = page("tester", "<TextRegion id=\"r1\"><Coords points=\"-1,0 100,0 100,100 0,100\"/></TextRegion>");

        assertThatThrownBy(() -> validation.toImportablePageXml(resource(xml), image -> Optional.empty()))
                .isInstanceOf(InvalidPageXmlException.class);
    }

    @Test
    void reportsWhatStaysInvalidAfterNormalizing() {
        String xml = page("Transkribus", "<TextRegion id=\"r1\"><Coords points=\"-1,0\"/></TextRegion>");

        assertThatThrownBy(() -> validation.toImportablePageXml(resource(xml), image -> Optional.empty()))
                .isInstanceOf(InvalidPageXmlException.class)
                .hasMessageContaining("Invalid PAGE XML");
    }

    private byte[] importable(String name) throws IOException {
        byte[] pageXml = validation.toImportablePageXml(new ByteArrayResource(fixture(name)), image -> Optional.empty())
                .orElseThrow();
        assertThat(validation.validatePageXml(new ByteArrayResource(pageXml)).valid()).isTrue();
        assertThat(PageXml.create().version(pageXml)).isEqualTo(PageXmlConversionService.PRIMARY_VERSION);
        return pageXml;
    }

    private static MetadataItem metadataItem(Page page, String name) {
        return page.metadata().metadataItems().stream()
                .filter(item -> name.equals(item.name()))
                .findFirst()
                .orElseThrow();
    }

    private static String points(Polygon polygon) {
        return polygon.points().stream().map(point -> point.x() + "," + point.y()).collect(Collectors.joining(" "));
    }

    private static byte[] fixture(String name) throws IOException {
        return new ClassPathResource("transkribus/" + name).getContentAsByteArray();
    }

    private static ByteArrayResource resource(String xml) {
        return new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8));
    }

    private static String page(String creator, String content) {
        return """
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2013-07-15">
                  <Metadata><Creator>%s</Creator><Created>2023-03-10T14:10:02</Created><LastChange>2023-03-10T14:10:02</LastChange></Metadata>
                  <Page imageFilename="page.png" imageWidth="200" imageHeight="200">
                %s
                  </Page>
                </PcGts>
                """.formatted(creator, content);
    }
}
