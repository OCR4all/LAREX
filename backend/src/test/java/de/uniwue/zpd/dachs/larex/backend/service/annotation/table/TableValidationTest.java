package de.uniwue.zpd.dachs.larex.backend.service.annotation.table;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableValidationTest {

    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());

    @TempDir
    Path directory;

    @Test
    void acceptsAConsistentTable() throws Exception {
        PageDto page = parse("""
                <TableRegion id="t" rows="1" columns="2">
                  <Coords points="0,0 200,0 200,100 0,100"/>
                  %s
                  %s
                  <Grid>
                    <GridPoints index="0" points="0,0 100,0 200,0"/>
                    <GridPoints index="1" points="0,100 100,100 200,100"/>
                  </Grid>
                </TableRegion>
                """.formatted(cell("a", 0, 0, ""), cell("b", 0, 1, "")));
        assertEquals(List.of(), TableValidation.validate(page));
    }

    @Test
    void reportsStructuralProblems() throws Exception {
        PageDto page = parse("""
                <TableRegion id="t" rows="3" columns="2">
                  <Coords points="0,0 200,0 200,200 0,200"/>
                  %s
                  %s
                  %s
                  <Grid>
                    <GridPoints index="0" points="0,0 100,0 200,0"/>
                    <GridPoints index="1" points="0,100 100,100 200,100"/>
                    <GridPoints index="2" points="0,200 100,200 200,200"/>
                  </Grid>
                </TableRegion>
                %s
                """.formatted(
                cell("a", 0, 0, " colSpan=\"2\""),
                cell("b", 0, 1, ""),
                cell("c", 5, 0, ""),
                cell("stray", 0, 0, "")));

        List<String> codes = TableValidation.validate(page).stream().map(TableIssue::code).toList();
        assertEquals(List.of(
                "dimension-mismatch",
                "cell-overlap",
                "cell-index-out-of-range",
                "uncovered-position",
                "uncovered-position",
                "role-outside-table"), codes);
    }

    @Test
    void reportsTableProblemsAsWriteWarnings() throws Exception {
        PageDto page = parse("""
                <TableRegion id="t" rows="1" columns="2">
                  <Coords points="0,0 200,0 200,100 0,100"/>
                  %s
                  <Grid>
                    <GridPoints index="0" points="0,0 100,0 200,0"/>
                    <GridPoints index="1" points="0,100 100,100 200,100"/>
                  </Grid>
                </TableRegion>
                """.formatted(cell("a", 0, 0, "")));
        AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());

        List<String> warnings = exporter.writeValidated(page, null, directory.resolve("out.xml")).warnings();

        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().startsWith("Table t: A table position is not covered by any cell (row 1, column 2)"), warnings.getFirst());
    }

    private PageDto parse(String regions) throws Exception {
        Path source = directory.resolve("source.xml");
        Files.writeString(source, """
                <PcGts xmlns="http://schema.primaresearch.org/PAGE/gts/pagecontent/2024-07-15">
                  <Metadata><Creator>tester</Creator><Created>2026-11-20T10:00:00</Created><LastChange>2026-11-20T10:00:00</LastChange></Metadata>
                  <Page imageFilename="page.png" imageWidth="1000" imageHeight="1500">
                %s
                  </Page>
                </PcGts>
                """.formatted(regions));
        return parser.parse(source, null);
    }

    private static String cell(String id, int row, int col, String extra) {
        return """
                <TextRegion id="%s">
                  <Coords points="0,0 10,0 10,10 0,10"/>
                  <Roles><TableCellRole rowIndex="%d" columnIndex="%d"%s/></Roles>
                </TextRegion>
                """.formatted(id, row, col, extra);
    }
}
