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
public class OcrFormatExportWriter {

    private final AnnotationProcessingService annotationProcessingService;

    public OcrFormatExportWriter(AnnotationProcessingService annotationProcessingService) {
        this.annotationProcessingService = annotationProcessingService;
    }

    DocumentExportService.StreamingDocumentExportResult render(Project project,
                                                               List<ExportPage> pages,
                                                               DocumentExportDto.ExportFormat format) throws IOException {
        XmlSchema schema = schema(format);
        String extension = "." + format.getFileExtension();
        if (pages.size() == 1) {
            ExportPage page = pages.getFirst();
            String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), schema, sourceXmlId(page));
            String fileName = DocumentExportFileNames.sanitizeFileName(page.page().getName(), "page") + extension;
            return new DocumentExportService.StreamingDocumentExportResult(
                    fileName,
                    format.getContentType(),
                    outputStream -> outputStream.write(xml.getBytes(StandardCharsets.UTF_8))
            );
        }

        String baseName = DocumentExportFileNames.sanitizeFileName(project.getName(), "project");
        return new DocumentExportService.StreamingDocumentExportResult(
                baseName + "." + zipSuffix(format) + ".zip",
                "application/zip",
                outputStream -> writeZipEntries(outputStream, pages, schema, extension)
        );
    }

    static String zipSuffix(DocumentExportDto.ExportFormat format) {
        return switch (format) {
            case FINE_READER_XML -> "finereader";
            case HOCR -> "hocr";
            default -> "alto";
        };
    }

    private static XmlSchema schema(DocumentExportDto.ExportFormat format) {
        return switch (format) {
            case ALTO_XML -> XmlSchema.ALTO_XML;
            case FINE_READER_XML -> XmlSchema.FINE_READER_XML;
            case HOCR -> XmlSchema.HOCR;
            default -> throw new IllegalArgumentException("Not an OCR format export: " + format);
        };
    }

    private void writeZipEntries(OutputStream outputStream,
                                 List<ExportPage> pages,
                                 XmlSchema schema,
                                 String extension) throws IOException {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        for (ExportPage page : pages) {
            String fileName = DocumentExportFileNames.sanitizeFileName(page.page().getName(), "page") + extension;
            zipOutputStream.putNextEntry(new ZipEntry(fileName));
            try {
                String xml = annotationProcessingService.exportAnnotationToXml(page.pageDto(), schema, sourceXmlId(page));
                zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            } finally {
                zipOutputStream.closeEntry();
            }
        }
        zipOutputStream.finish();
    }

    private String sourceXmlId(ExportPage page) {
        return page.pageXml() == null ? null : page.pageXml().getId();
    }
}
