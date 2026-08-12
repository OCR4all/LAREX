package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;

public class DocumentExportDto {

    public enum TextLevel {
        PAGE,
        REGION,
        TEXT_LINE
    }

    public enum SpreadsheetProfile {
        PAGE_METADATA,
        TAGS,
        REGIONS
    }

    public enum PdfProfile {
        SEARCHABLE,
        IMAGES_ONLY,
        TEXT_PAGES,
        PDFA_SEARCHABLE
    }

    public enum TeiProfile {
        STANDARD,
        LAYOUT
    }

    public enum ExportFormat {
        PAGE_XML("xml", "application/xml"),
        ALTO_XML("alto.xml", "application/xml"),
        TXT("txt", "text/plain; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        TEI("tei.xml", "application/tei+xml"),
        CSV("csv", "text/csv; charset=UTF-8"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String fileExtension;
        private final String contentType;

        ExportFormat(String fileExtension, String contentType) {
            this.fileExtension = fileExtension;
            this.contentType = contentType;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isRenderedOutput() {
            return this == TXT || this == PDF || this == DOCX || this == TEI || this == ALTO_XML;
        }

        public boolean supportsPageExportEndpoint() {
            return this == PAGE_XML || this == ALTO_XML || this == TXT || this == PDF || this == DOCX || this == TEI;
        }

        public boolean supportsProjectExportEndpoint() {
            return this != PAGE_XML;
        }

        public boolean supportsProjectPackageEmbedding() {
            return this != PAGE_XML;
        }
    }

    public record DocxOptions(
            Boolean preserveLineBreaks,
            Boolean forcePageBreaks,
            Boolean includeImageNames,
            Boolean markUnclearWords,
            Double unclearConfidenceThreshold
    ) {
    }

    public record PageExportRequest(
            ExportFormat format,
            String targetPageXmlVersion,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex,
            PdfProfile pdfProfile,
            TeiProfile teiProfile,
            List<SpreadsheetProfile> spreadsheetProfiles,
            DocxOptions docxOptions
    ) {
    }

    public record ProjectExportRequest(
            ExportFormat format,
            List<String> pageIds,
            String targetPageXmlVersion,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex,
            PdfProfile pdfProfile,
            TeiProfile teiProfile,
            List<SpreadsheetProfile> spreadsheetProfiles,
            DocxOptions docxOptions
    ) {
    }

    public record EmbeddedProjectOutputRequest(
            ExportFormat format,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex,
            PdfProfile pdfProfile,
            TeiProfile teiProfile,
            List<SpreadsheetProfile> spreadsheetProfiles,
            DocxOptions docxOptions
    ) {
    }
}
