package de.uniwue.zpd.dachs.larex.backend.dto;

import java.util.List;

public class DocumentExportDto {

    public enum TextLevel {
        PAGE,
        REGION,
        TEXT_LINE
    }

    public enum ExportFormat {
        PAGE_XML("xml", "application/xml"),
        TXT("txt", "text/plain; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        TEI("tei.xml", "application/tei+xml"),
        ALTO_XML("alto.xml", "application/xml");

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
            return this == TXT || this == PDF || this == DOCX || this == TEI;
        }

        public boolean supportsPageExportEndpoint() {
            return this == PAGE_XML || isRenderedOutput();
        }

        public boolean supportsProjectExportEndpoint() {
            return isRenderedOutput();
        }

        public boolean supportsProjectPackageEmbedding() {
            return isRenderedOutput();
        }
    }

    public record PageExportRequest(
            ExportFormat format,
            String targetPageXmlVersion,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex
    ) {
    }

    public record ProjectExportRequest(
            ExportFormat format,
            List<String> pageIds,
            String targetPageXmlVersion,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex
    ) {
    }

    public record EmbeddedProjectOutputRequest(
            ExportFormat format,
            Boolean includePageDelimiters,
            TextLevel textLevel,
            Integer textVariantIndex
    ) {
    }
}
