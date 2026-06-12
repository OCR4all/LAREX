package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

@Component
public class DocxExportWriter {

    private static final double UNCLEAR_CONFIDENCE_THRESHOLD = 0.75d;

    DocumentExportService.StreamingDocumentExportResult render(String baseName,
                                                               Project project,
                                                               List<ExportPage> pages,
                                                               ResolvedDocxOptions options,
                                                               boolean pageScope) {
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + ".docx",
                DocumentExportDto.ExportFormat.DOCX.getContentType(),
                outputStream -> write(outputStream, project, pages, options, pageScope)
        );
    }

    private void write(OutputStream outputStream,
                       Project project,
                       List<ExportPage> pages,
                       ResolvedDocxOptions options,
                       boolean pageScope) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.setText(pages.size() == 1 ? pages.getFirst().page().getName() : project.getName());

            for (int i = 0; i < pages.size(); i++) {
                ExportPage page = pages.get(i);

                XWPFParagraph heading = document.createParagraph();
                heading.setStyle("Heading1");
                XWPFRun headingRun = heading.createRun();
                headingRun.setBold(true);
                headingRun.setFontSize(14);
                headingRun.setText(page.page().getName());

                if (options.includeImageNames() && page.image() != null && page.image().getFileName() != null) {
                    XWPFParagraph imageParagraph = document.createParagraph();
                    XWPFRun imageRun = imageParagraph.createRun();
                    imageRun.setItalic(true);
                    imageRun.setUnderline(UnderlinePatterns.SINGLE);
                    imageRun.setText(page.image().getFileName());
                }

                for (ExportRegion region : page.regions()) {
                    if (!region.hasText()) {
                        continue;
                    }
                    XWPFParagraph paragraph = document.createParagraph();
                    appendRegion(paragraph, region, options);
                }

                if (!pageScope && options.forcePageBreaks() && i < pages.size() - 1) {
                    XWPFParagraph breakParagraph = document.createParagraph();
                    XWPFRun breakRun = breakParagraph.createRun();
                    breakRun.addBreak(BreakType.PAGE);
                }
            }

            document.write(outputStream);
        }
    }

    private void appendRegion(XWPFParagraph paragraph,
                              ExportRegion region,
                              ResolvedDocxOptions options) {
        if (options.preserveLineBreaks()) {
            List<ExportTextLine> lines = region.lines().stream().filter(ExportTextLine::hasText).toList();
            if (!lines.isEmpty()) {
                for (int i = 0; i < lines.size(); i++) {
                    appendLine(paragraph, lines.get(i), options.markUnclearWords());
                    if (i < lines.size() - 1) {
                        paragraph.createRun().addBreak();
                    }
                }
                return;
            }
        }

        if (!region.lines().isEmpty()) {
            boolean firstToken = true;
            for (ExportTextLine line : region.lines()) {
                if (!line.hasText()) {
                    continue;
                }
                if (!firstToken) {
                    paragraph.createRun().setText(" ");
                }
                appendLineInline(paragraph, line, options.markUnclearWords());
                firstToken = false;
            }
            return;
        }

        XWPFRun run = paragraph.createRun();
        run.setText(nullToEmpty(region.text()));
    }

    private void appendLine(XWPFParagraph paragraph,
                            ExportTextLine line,
                            boolean markUnclearWords) {
        if (!line.words().isEmpty()) {
            boolean first = true;
            for (ExportWord word : line.words()) {
                if (!word.hasText()) {
                    continue;
                }
                if (!first) {
                    paragraph.createRun().setText(" ");
                }
                XWPFRun run = paragraph.createRun();
                styleRunForUnclear(run, markUnclearWords && isUnclear(word, line));
                run.setText(word.text());
                first = false;
            }
            return;
        }

        XWPFRun run = paragraph.createRun();
        styleRunForUnclear(run, markUnclearWords && isUnclear(line));
        run.setText(nullToEmpty(line.text()));
    }

    private void appendLineInline(XWPFParagraph paragraph,
                                  ExportTextLine line,
                                  boolean markUnclearWords) {
        appendLine(paragraph, line, markUnclearWords);
    }

    private void styleRunForUnclear(XWPFRun run, boolean unclear) {
        if (!unclear) {
            return;
        }
        run.setItalic(true);
        run.setTextHighlightColor("yellow");
    }

    private boolean isUnclear(ExportWord word, ExportTextLine line) {
        Double confidence = firstNonNull(word.confidence(), word.variantConfidence(), line.variantConfidence(), line.confidence());
        return confidence != null && confidence < UNCLEAR_CONFIDENCE_THRESHOLD;
    }

    private boolean isUnclear(ExportTextLine line) {
        Double confidence = firstNonNull(line.variantConfidence(), line.confidence());
        return confidence != null && confidence < UNCLEAR_CONFIDENCE_THRESHOLD;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
