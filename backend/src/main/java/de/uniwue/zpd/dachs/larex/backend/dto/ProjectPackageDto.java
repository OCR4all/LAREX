package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ProjectPackageDto {

    public static final String DEFAULT_SCHEMA_VERSION = "1.0";

    public record ExportRequest(
            List<String> pageIds,
            String targetPageXmlVersion
    ) {
        public ExportRequest(List<String> pageIds) {
            this(pageIds, null);
        }
    }

    public record ImportRequest(
            @NotBlank(message = "workspaceId is required")
            String workspaceId
    ) {
    }

    public record PackageManifest(
            String schemaVersion,
            LocalDateTime exportedAt,
            String sourceWorkspaceId,
            String sourceWorkspaceName,
            ProjectSnapshot project,
            List<PageSnapshot> pages,
            UtilityReferences utilityReferences,
            List<FileEntry> files,
            List<XmlVersionEntry> xmlVersions,
            List<String> warnings
    ) {
    }

    public record ProjectSnapshot(
            String sourceProjectId,
            String name,
            String description,
            List<String> tags,
            LocalDateTime sourceCreated,
            LocalDateTime sourceUpdated,
            boolean locked,
            String lockedReason
    ) {
    }

    public record PageSnapshot(
            String sourcePageId,
            String name,
            String description,
            List<String> tags,
            LocalDateTime sourceCreated,
            LocalDateTime sourceUpdated,
            boolean locked,
            String lockedReason
    ) {
    }

    public enum FileKind {
        IMAGE,
        XML
    }

    public record FileEntry(
            String sourceId,
            String sourcePageId,
            FileKind kind,
            String fileName,
            String mimeType,
            Long fileSize,
            String variant,
            String baseName,
            String archivePath,
            XmlSchema xmlSchema,
            String xmlSchemaVersion,
            String thumbnailArchivePath,
            LocalDateTime sourceCreated,
            LocalDateTime sourceUpdated
    ) {
    }

    public record XmlVersionEntry(
            String sourceVersionId,
            String sourceXmlId,
            Integer versionNumber,
            String archivePath,
            Long fileSize,
            String userId,
            String comment,
            LocalDateTime sourceCreated
    ) {
    }

    public record UtilityReference(
            String sourceId,
            String name,
            LocalDateTime sourceCreated,
            LocalDateTime sourceUpdated,
            String snapshotPath
    ) {
    }

    public record UtilityReferences(
            UtilityReference codec,
            UtilityReference labelSet,
            UtilityReference tagSet
    ) {
    }

    public record ImportResult(
            String workspaceId,
            String projectId,
            String projectName,
            int pageCount,
            int imageCount,
            int xmlCount,
            int xmlVersionCount,
            List<String> warnings,
            Map<String, String> utilitySourceToTargetIds
    ) {
    }
}
