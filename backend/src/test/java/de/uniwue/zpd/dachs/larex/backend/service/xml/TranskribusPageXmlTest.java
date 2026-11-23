package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.MetadataItem;
import com.maxnth.page4j.model.OrderedGroup;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.RegionRef;
import com.maxnth.page4j.model.Relation;
import com.maxnth.page4j.model.TableCellRole;
import com.maxnth.page4j.model.TableRegion;
import com.maxnth.page4j.model.TextEquiv;
import com.maxnth.page4j.model.TextLine;
import com.maxnth.page4j.model.TextRegion;
import com.maxnth.page4j.model.value.Language;
import com.maxnth.page4j.model.value.RelationType;
import com.maxnth.page4j.model.value.Script;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TranskribusPageXmlTest {

    private static final String REGION =
            "<TextRegion id=\"r1\"><Coords points=\"0,0 100,0 100,100 0,100\"/></TextRegion>";

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
    void removesTranskribusTagsPropertiesAndLinks() {
        String xml = page("Transkribus", """
                <TextRegion id="r1">
                  <Tag name="person"><Property key="firstname" value="Anna"/></Tag>
                  <Link ref="r1" type="sameAs"/>
                  <Coords points="0,0 100,0 100,100 0,100"/>
                </TextRegion>""");

        byte[] pageXml = validation.toImportablePageXml(resource(xml), image -> Optional.empty()).orElseThrow();

        assertThat(new String(pageXml, StandardCharsets.UTF_8)).doesNotContain("<Tag", "<Property", "<Link");
    }

    @Test
    void movesImageTransformAttributesToPageLabels() throws IOException {
        String xml = page("Transkribus", REGION)
                .replace("imageHeight=\"200\"", "imageHeight=\"200\" imageScalingX=\"0.5\" imageRotation=\"90\"");

        byte[] pageXml = importableBytes(xml);

        assertThat(new String(pageXml, StandardCharsets.UTF_8)).doesNotContain("imageRotation=", "imageScalingX=");
        assertThat(read(pageXml).labels()).singleElement().satisfies(labels -> {
            assertThat(labels.externalModel()).isEqualTo(TranskribusPageXml.IMAGE_TRANSFORM_MODEL);
            assertThat(labels.labels()).extracting(label -> label.type() + "=" + label.value())
                    .containsExactly("imageRotation=90", "imageScalingX=0.5");
        });
    }

    @Test
    void marksLabelCellsAsTableHeaders() throws IOException {
        String xml = new String(fixture("table.xml"), StandardCharsets.UTF_8)
                .replace("label=\"false\"", "label=\"true\"");

        TableRegion table = read(importableBytes(xml)).find("t1", TableRegion.class).orElseThrow();

        assertThat(table.regions()).extracting(region -> region.tableCellRole().header())
                .containsExactly(null, null, true);
    }

    @Test
    void convertsUnicodeAlternativesOfEachTextEquivToIndexedTextEquivs() throws IOException {
        String xml = page("Transkribus", """
                <TextRegion id="r1" primaryScript="Latin">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                  <TextLine id="l1">
                    <Coords points="0,0 100,0 100,50 0,50"/>
                    <TextEquiv conf="0.9">
                      <Unicode>Haus</Unicode>
                      <UnicodeAlternative>Hans</UnicodeAlternative>
                      <UnicodeAlternative>Maus</UnicodeAlternative>
                    </TextEquiv>
                    <TextStyle fontFamily="Fraktur"/>
                  </TextLine>
                  <TextLine id="l2">
                    <Coords points="0,50 100,50 100,100 0,100"/>
                    <TextEquiv><Unicode>Hof</Unicode><UnicodeAlternative>Hut</UnicodeAlternative></TextEquiv>
                  </TextLine>
                  <TextEquiv><Unicode>Haus Hof</Unicode></TextEquiv>
                </TextRegion>""");

        Page page = read(importableBytes(xml));

        assertThat(page.find("l1", TextLine.class).orElseThrow().textEquivs())
                .extracting(TextEquiv::index, TextEquiv::conf, TextEquiv::unicode)
                .containsExactly(tuple(null, 0.9, "Haus"), tuple(1, null, "Hans"), tuple(2, null, "Maus"));
        assertThat(page.find("l2", TextLine.class).orElseThrow().textEquivs())
                .extracting(TextEquiv::index, TextEquiv::unicode)
                .containsExactly(tuple(null, "Hof"), tuple(1, "Hut"));
        assertThat(page.find("r1", TextRegion.class).orElseThrow().primaryScript())
                .isEqualTo(Script.fromXml("Latn - Latin"));
    }

    @Test
    void keepsUserDefinedAttributes() throws IOException {
        String userDefined = "<UserDefined><UserAttribute name=\"hand\" type=\"xsd:string\" value=\"A\"/></UserDefined>";
        String xml = page("Transkribus", """
                %s
                <TextRegion id="r1">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                  %s
                  <TextLine id="l1"><Coords points="0,0 100,0 100,50 0,50"/>%s</TextLine>
                </TextRegion>""".formatted(userDefined, userDefined, userDefined))
                .replace("</LastChange>", "</LastChange>" + userDefined);

        Page page = read(importableBytes(xml));

        assertThat(page.metadata().userAttributes()).extracting(attribute -> attribute.value()).containsExactly("A");
        assertThat(page.userAttributes()).extracting(attribute -> attribute.value()).containsExactly("A");
        assertThat(page.find("r1", TextRegion.class).orElseThrow().userAttributes()).hasSize(1);
        assertThat(page.find("l1", TextLine.class).orElseThrow().userAttributes()).hasSize(1);
    }

    @Test
    void readsContentNewerThanTheDeclaredPageVersion() throws IOException {
        String xml = page("Transkribus", """
                <Relations>
                  <Relation type="link"><RegionRef regionRef="r1"/><RegionRef regionRef="relation_1"/></Relation>
                </Relations>
                <TextRegion id="r1" primaryScript="Latin">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                  <TextRegion id="r2"><Coords points="0,0 50,0 50,50 0,50" conf="0.5"/></TextRegion>
                </TextRegion>
                """ + regions("relation_1"))
                .replace("imageHeight=\"200\"", "imageHeight=\"200\" primaryLanguage=\"German\"");

        Page page = read(importableBytes(xml));

        assertThat(page.primaryLanguage()).isEqualTo(Language.fromXml("German"));
        assertThat(page.find("r1", TextRegion.class).orElseThrow().primaryScript())
                .isEqualTo(Script.fromXml("Latn - Latin"));
        assertThat(page.relations()).extracting(Relation::id, Relation::sourceRegionRef, Relation::targetRegionRef)
                .containsExactly(tuple("relation_2", "r1", "relation_1"));
    }

    @Test
    void readsUserDefinedAttributesWithANamespacePrefix() throws IOException {
        String xml = """
                <pc:PcGts xmlns:pc="http://schema.primaresearch.org/PAGE/gts/pagecontent/2013-07-15">
                  <pc:Metadata><pc:Creator>Transkribus</pc:Creator><pc:Created>2023-03-10T14:10:02</pc:Created><pc:LastChange>2023-03-10T14:10:02</pc:LastChange></pc:Metadata>
                  <pc:Page imageFilename="page.png" imageWidth="200" imageHeight="200">
                    <pc:UserDefined><pc:UserAttribute name="hand" value="A"/></pc:UserDefined>
                  </pc:Page>
                </pc:PcGts>
                """;

        assertThat(read(importableBytes(xml)).userAttributes()).hasSize(1);
    }

    @Test
    void splitsRelationsBetweenMoreThanTwoObjects() throws IOException {
        String xml = page("Transkribus", """
                <Relations>
                  <Relation type="link"><RegionRef regionRef="r1"/><RegionRef regionRef="r2"/><RegionRef regionRef="r3"/></Relation>
                  <Relation type="join"><RegionRef regionRef="r1"/><RegionRef regionRef="r3"/></Relation>
                </Relations>
                """ + regions("r1", "r2", "r3"));

        Page page = read(importableBytes(xml));

        assertThat(page.relations()).extracting(Relation::type, Relation::sourceRegionRef, Relation::targetRegionRef)
                .containsExactly(
                        tuple(RelationType.LINK, "r1", "r2"),
                        tuple(RelationType.LINK, "r2", "r3"),
                        tuple(RelationType.JOIN, "r1", "r3"));
        assertThat(page.relations()).extracting(Relation::id).doesNotContainNull().doesNotHaveDuplicates();
    }

    @Test
    void removesEmptyRelationsAndReadingOrderGroups() throws IOException {
        Page empty = read(importableBytes(page("Transkribus", """
                <ReadingOrder><OrderedGroup id="ro" caption="Regions reading order"/></ReadingOrder>
                <Relations/>
                """)));

        assertThat(empty.readingOrder()).isNull();
        assertThat(empty.relations()).isEmpty();

        Page filled = read(importableBytes(page("Transkribus", """
                <ReadingOrder><OrderedGroup id="ro">
                  <OrderedGroupIndexed id="g1" index="0"/>
                  <RegionRefIndexed index="1" regionRef="r1"/>
                </OrderedGroup></ReadingOrder>
                """ + REGION)));

        assertThat(((OrderedGroup) filled.readingOrder().group()).members()).singleElement()
                .isInstanceOf(RegionRef.class);
    }

    @Test
    void rewritesTimestampsThatAreNotXsdDateTimes() throws IOException {
        String xml = page("Transkribus", REGION)
                .replace("<Created>2023-03-10T14:10:02", "<Created>2023-3-1T4:10:02.5+0100");

        Page page = read(importableBytes(xml));

        assertThat(page.metadata().created().xmlValue()).isEqualTo("2023-03-01T04:10:02.5+01:00");
        assertThat(page.metadata().lastChange().xmlValue()).isEqualTo("2023-03-10T14:10:02");
    }

    @Test
    void normalizesTimestampsOnlyWhenTheyDescribeADate() {
        assertThat(TranskribusPageXml.normalizeTimestamp(" 2023-03-10T14:10:02.118+01:00 "))
                .isEqualTo("2023-03-10T14:10:02.118+01:00");
        assertThat(TranskribusPageXml.normalizeTimestamp("2023-03-10 14:10")).isEqualTo("2023-03-10T14:10:00");
        assertThat(TranskribusPageXml.normalizeTimestamp("2023-03-10T14:10:02.Z")).isEqualTo("2023-03-10T14:10:02Z");
        assertThat(TranskribusPageXml.normalizeTimestamp("2023-13-10T14:10:02")).isNull();
        assertThat(TranskribusPageXml.normalizeTimestamp("yesterday")).isNull();
        assertThat(TranskribusPageXml.normalizeTimestamp("")).isNull();
    }

    @Test
    void clampsTableGridPoints() throws IOException {
        String xml = page("Transkribus", """
                <TableRegion id="t1">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                  <Grid>
                    <GridPoints index="0" points="-5,0 250,0"/>
                    <GridPoints index="1" points="0,100 100,100"/>
                  </Grid>
                </TableRegion>""").replace("2013-07-15", "2019-07-15");

        TableRegion table = read(importableBytes(xml)).find("t1", TableRegion.class).orElseThrow();

        assertThat(points(table.grid().gridPoints().getFirst().points())).isEqualTo("0,0 200,0");
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
    void reportsWhatStaysInvalidAfterNormalizing() throws IOException {
        String xml = page("Transkribus", "<TextRegion id=\"r1\"><Coords points=\"-1,0\"/></TextRegion>");

        assertThatThrownBy(() -> validation.toImportablePageXml(resource(xml), image -> Optional.empty()))
                .isInstanceOf(InvalidPageXmlException.class)
                .hasMessageContaining("Invalid PAGE XML");
    }

    private byte[] importable(String name) throws IOException {
        return importableBytes(fixture(name));
    }

    private byte[] importableBytes(String xml) throws IOException {
        return importableBytes(xml.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] importableBytes(byte[] xml) throws IOException {
        byte[] pageXml = validation.toImportablePageXml(new ByteArrayResource(xml), image -> Optional.empty())
                .orElseThrow();
        assertThat(validation.validatePageXml(new ByteArrayResource(pageXml)).valid()).isTrue();
        assertThat(PageXml.create().version(pageXml)).isEqualTo(PageXmlConversionService.PRIMARY_VERSION);
        return pageXml;
    }

    private static Page read(byte[] pageXml) throws IOException {
        return PageXml.create().read(pageXml).page();
    }

    private static String regions(String... ids) {
        return Arrays.stream(ids).map(id -> REGION.replace("r1", id)).collect(Collectors.joining());
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
