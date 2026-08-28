package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.time.LocalDateTime;

public class ProjectBatchExportDto {

    public record JobResponse(
            String id,
            String workspaceId,
            ExportMode mode,
            String status,
            String fileName,
            Long fileSize,
            String checksumSha256,
            String errorMessage,
            LocalDateTime created,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime expiresAt
    ) {}

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
            DocumentExportDto.DocxOptions docxOptions,
            DocumentExportDto.ImageVariantSelection imageVariantSelection,
            Boolean includeXmlHistory
    ) {
        public ExportRequest(List<String> projectIds,
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
                             DocumentExportDto.DocxOptions docxOptions) {
            this(projectIds, mode, targetPageXmlVersion, embeddedOutputs, format, includePageDelimiters,
                    textLevel, textVariantIndex, pdfProfile, teiProfile, spreadsheetProfiles, docxOptions, null, false);
        }

        public ProjectPackageDto.ExportRequest toPackageExportRequest() {
            return new ProjectPackageDto.ExportRequest(
                    null,
                    targetPageXmlVersion,
                    embeddedOutputs,
                    Boolean.TRUE.equals(includeXmlHistory)
            );
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
                    docxOptions,
                    imageVariantSelection
            );
        }
    }
}
