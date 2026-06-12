package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.DocumentExportDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.application.AnnotationProcessingService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

@Component
public class AltoExportWriter {

    private final AnnotationProcessingService annotationProcessingService;

    public AltoExportWriter(AnnotationProcessingService annotationProcessingService) {
        this.annotationProcessingService = annotationProcessingService;
    }

    DocumentExportService.StreamingDocumentExportResult render(Project project, List<ExportPage> pages) throws IOException {
        if (pages.size() == 1) {
            ExportPage page = pages.getFirst();
            String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), XmlSchema.ALTO_XML, page.pageXml().getId());
            String fileName = DocumentExportFileNames.sanitizeFileName(page.page().getName(), "page") + ".alto.xml";
            return new DocumentExportService.StreamingDocumentExportResult(
                    fileName,
                    DocumentExportDto.ExportFormat.ALTO_XML.getContentType(),
                    outputStream -> outputStream.write(xml.getBytes(StandardCharsets.UTF_8))
            );
        }

        String baseName = DocumentExportFileNames.sanitizeFileName(project.getName(), "project");
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + ".alto.zip",
                "application/zip",
                outputStream -> writeAltoZipEntries(outputStream, pages)
        );
    }

    private void writeAltoZipEntries(OutputStream outputStream, List<ExportPage> pages) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        for (ExportPage page : pages) {
            String fileName = DocumentExportFileNames.sanitizeFileName(page.page().getName(), "page") + ".alto.xml";
            zipOutputStream.putNextEntry(new ZipEntry(fileName));
            try {
                String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), XmlSchema.ALTO_XML, page.pageXml().getId());
                zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.finish();
    }
}
