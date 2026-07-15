package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class ProjectBatchExportDto {

    public enum ExportMode {
        BASIC,
        CONVERTED,
        PACKAGE
    }

    public record ExportRequest(
            @NotEmpty(message = "At least one project is required")
            @Size(max = 100, message = "Cannot export more than 100 projects at once")
            List<@NotBlank(message = "Project ID must not be blank") String> projectIds,
            @NotNull(message = "Export mode is required")
            ExportMode mode,
            String targetPageXmlVersion,
            List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs,
            DocumentExportDto.ExportFormat format,
            Boolean includePageDelimiters,
            DocumentExportDto.TextLevel textLevel,
            Integer textVariantIndex,
            DocumentExportDto.PdfProfile pdfProfile,
            DocumentExportDto.TeiProfile teiProfile,
            List<DocumentExportDto.SpreadsheetProfile> spreadsheetProfiles,
            DocumentExportDto.DocxOptions docxOptions
    ) {
        public ProjectPackageDto.ExportRequest toPackageExportRequest() {
            return new ProjectPackageDto.ExportRequest(null, targetPageXmlVersion, embeddedOutputs);
        }

        public DocumentExportDto.ProjectExportRequest toDocumentExportRequest() {
            return new DocumentExportDto.ProjectExportRequest(
                    format,
                    null,
                    targetPageXmlVersion,
                    includePageDelimiters,
                    textLevel,
                    textVariantIndex,
                    pdfProfile,
                    teiProfile,
                    spreadsheetProfiles,
                    docxOptions
            );
        }
    }
}
