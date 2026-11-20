package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ActionResultPageMergeServiceTest {

    @TempDir
    Path tempDir;

    private final ActionResultPageMergeService service = new ActionResultPageMergeService();

    @Test
    void replacingASelectedTableTakesTheReturnedCellsAndGrid() throws IOException {
        PageDto existing = page("existing.xml", """
                <TableRegion id="t"><Coords points="0,0 200,0 200,100 0,100"/></TableRegion>
                <TextRegion id="other"><Coords points="0,300 10,300 10,310"/></TextRegion>
                """);
        PageDto incoming = page("incoming.xml", """
                <TableRegion id="t" rows="1" columns="2">
                  <Coords points="0,0 100,0 200,0 200,100 100,100 0,100"/>
                  <TextRegion id="a"><Coords points="0,0 100,0 100,100 0,100"/>
                    <Roles><TableCellRole rowIndex="0" columnIndex="0"/></Roles></TextRegion>
                  <TextRegion id="b"><Coords points="100,0 200,0 200,100 100,100"/>
                    <Roles><TableCellRole rowIndex="0" columnIndex="1"/></Roles></TextRegion>
                  <Grid>
                    <GridPoints index="0" points="0,0 100,0 200,0"/>
                    <GridPoints index="1" points="0,100 100,100 200,100"/>
                  </Grid>
                </TableRegion>
                <TextRegion id="other"><Coords points="5,305 15,305 15,315"/></TextRegion>
                """);

        PageDto merged = service.replaceTargetRegions(existing, incoming, Set.of("t"));

        RegionDto table = merged.regions().getFirst();
        assertThat(table.nestedRegions()).extracting(RegionDto::id).containsExactly("a", "b");
        assertThat(table.nestedRegions().getFirst().roles().tableCellRole().columnIndex()).isZero();
        assertThat(table.grid().rows()).hasSize(2);
        assertThat(table.rows()).isEqualTo(1);
        assertThat(table.columns()).isEqualTo(2);
        assertThat(table.coords()).isEqualTo(incoming.regions().getFirst().coords());
        assertThat(merged.regions().get(1)).isEqualTo(existing.regions().get(1));
    }

    @Test
    void replacingASelectedCellKeepsItsRoleAndTheTableGrid() throws IOException {
        String table = """
                <TableRegion id="t" rows="1" columns="1">
                  <Coords points="0,0 100,0 100,100 0,100"/>
                  <TextRegion id="a"><Coords points="0,0 100,0 100,100 0,100"/>
                    <Roles><TableCellRole rowIndex="0" columnIndex="0" header="true"/></Roles>%s</TextRegion>
                  <Grid>
                    <GridPoints index="0" points="0,0 100,0"/>
                    <GridPoints index="1" points="0,100 100,100"/>
                  </Grid>
                </TableRegion>
                """;
        PageDto existing = page("existing.xml", table.formatted(""));
        PageDto incoming = page("incoming.xml", table.formatted("""
                <TextLine id="l1"><Coords points="10,10 90,10 90,40 10,40"/></TextLine>"""));

        PageDto merged = service.replaceTargetRegions(existing, incoming, Set.of("a"));

        RegionDto cell = merged.regions().getFirst().nestedRegions().getFirst();
        assertThat(cell.textLines()).extracting("id").containsExactly("l1");
        assertThat(cell.roles().tableCellRole().header()).isTrue();
        assertThat(merged.regions().getFirst().grid()).isEqualTo(existing.regions().getFirst().grid());
    }

    private PageDto page(String name, String regions) throws IOException {
        Path source = tempDir.resolve(name);
        Files.writeString(source, """
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
                  <Metadata><Creator>tester</Creator><Created>2026-11-20T10:00:00</Created><LastChange>2026-11-20T10:00:00</LastChange></Metadata>
                  <Page imageFilename="alpha.png" imageWidth="1000" imageHeight="1500">
                %s
                  </Page>
                </PcGts>
                """.formatted(regions));
        return new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(source, null);
    }
}
