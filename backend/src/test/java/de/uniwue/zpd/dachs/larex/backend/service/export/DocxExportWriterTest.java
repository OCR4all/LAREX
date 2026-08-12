package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxExportWriterTest {

    @Test
    void marksWordsBelowConfiguredConfidenceThreshold() throws Exception {
        Project project = new Project("Project", null, null);
        Page page = new Page("Page", null, project);
        ExportTextLine line = new ExportTextLine(
                "line-1",
                "unclear clear",
                null,
                null,
                null,
                null,
                List.of(
                        new ExportWord("word-1", "unclear", null, 0.8d, null),
                        new ExportWord("word-2", "clear", null, 0.95d, null)
                )
        );
        ExportRegion region = new ExportRegion(
                "region-1", null, RegionKind.TextRegion, null, null, null,
                null, null, null, line.text(), List.of(line)
        );
        ExportPage exportPage = new ExportPage(page, null, null, List.of(region), null, null);
        ResolvedDocxOptions options = new ResolvedDocxOptions(true, false, false, true, 0.9d);

        var result = new DocxExportWriter().render("export", project, List.of(exportPage), options, true);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        result.writer().write(output);

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(output.toByteArray()))) {
            List<XWPFRun> runs = document.getParagraphs().stream()
                    .flatMap(paragraph -> paragraph.getRuns().stream())
                    .toList();
            XWPFRun unclearRun = runs.stream().filter(run -> "unclear".equals(run.text())).findFirst().orElseThrow();
            XWPFRun clearRun = runs.stream().filter(run -> "clear".equals(run.text())).findFirst().orElseThrow();

            assertTrue(unclearRun.isItalic());
            assertTrue(unclearRun.isHighlighted());
            assertFalse(clearRun.isItalic());
            assertFalse(clearRun.isHighlighted());
        }
    }
}
