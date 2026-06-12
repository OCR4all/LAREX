package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TextDocumentExportWriter {

    private final PlainTextExportWriter plainTextExportWriter;
    private final DocxExportWriter docxExportWriter;
    private final TeiExportWriter teiExportWriter;
    private final PdfExportWriter pdfExportWriter;

    public TextDocumentExportWriter(PlainTextExportWriter plainTextExportWriter,
                                    DocxExportWriter docxExportWriter,
                                    TeiExportWriter teiExportWriter,
                                    PdfExportWriter pdfExportWriter) {
        this.plainTextExportWriter = plainTextExportWriter;
        this.docxExportWriter = docxExportWriter;
        this.teiExportWriter = teiExportWriter;
        this.pdfExportWriter = pdfExportWriter;
    }

    DocumentExportService.StreamingDocumentExportResult renderText(String baseName,
                                                                   List<ExportPage> pages,
                                                                   boolean includePageDelimiters,
                                                                   DocumentExportDto.TextLevel textLevel,
                                                                   int textVariantIndex) {
        return plainTextExportWriter.render(baseName, pages, includePageDelimiters, textLevel, textVariantIndex);
    }

    DocumentExportService.StreamingDocumentExportResult renderDocx(String baseName,
                                                                   Project project,
                                                                   List<ExportPage> pages,
                                                                   ResolvedDocxOptions options,
                                                                   boolean pageScope) {
        return docxExportWriter.render(baseName, project, pages, options, pageScope);
    }

    DocumentExportService.StreamingDocumentExportResult renderTei(String baseName,
                                                                  Project project,
                                                                  List<ExportPage> pages,
                                                                  DocumentExportDto.TeiProfile teiProfile) {
        return teiExportWriter.render(baseName, project, pages, teiProfile);
    }

    DocumentExportService.StreamingDocumentExportResult renderPdf(String baseName,
                                                                  Project project,
                                                                  List<ExportPage> pages,
                                                                  DocumentExportDto.PdfProfile pdfProfile) {
        return pdfExportWriter.render(baseName, project, pages, pdfProfile);
    }
}
