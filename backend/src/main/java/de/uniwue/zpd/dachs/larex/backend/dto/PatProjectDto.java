package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PatProjectDto {

    public record ProjectSummaryResponse(
            String id,
            String workspaceId,
            String name,
            String description,
            List<String> tags,
            int pageCount,
            LocalDateTime created,
            LocalDateTime updated
    ) {
    }

    public record ProjectDetailResponse(
            String id,
            String workspaceId,
            String name,
            String description,
            List<String> tags,
            int pageCount,
            LocalDateTime created,
            LocalDateTime updated,
            List<PageDetailResponse> pages
    ) {
    }

    public record PageDetailResponse(
            String id,
            String name,
            String description,
            List<String> tags,
            List<String> imageVariants,
            List<String> xmlVariants,
            LocalDateTime created,
            LocalDateTime updated,
            List<ImageFileResponse> images,
            List<XmlFileResponse> xmlFiles
    ) {
    }

    public record ImageFileResponse(
            String id,
            String fileName,
            String variant,
            String baseName,
            String mimeType,
            Long fileSize
    ) {
    }

    public record XmlFileResponse(
            String id,
            String fileName,
            String variant,
            String schema,
            String schemaVersion,
            String mimeType,
            Long fileSize
    ) {
    }
}
