package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ProjectPackageDto {

    public static final String DEFAULT_SCHEMA_VERSION = "1.0";

    public record ExportRequest(
            List<String> pageIds,
            String targetPageXmlVersion,
            List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs,
            Boolean includeXmlHistory
    ) {
        public ExportRequest(List<String> pageIds) {
            this(pageIds, null, null, false);
        }

        public ExportRequest(List<String> pageIds, String targetPageXmlVersion) {
            this(pageIds, targetPageXmlVersion, null, false);
        }

        public ExportRequest(List<String> pageIds,
                             String targetPageXmlVersion,
                             List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs) {
            this(pageIds, targetPageXmlVersion, embeddedOutputs, false);
        }

        public boolean includeXmlHistoryResolved() {
            return Boolean.TRUE.equals(includeXmlHistory);
        }
    }

    public record CreateReleaseRequest(
            String versionTag,
            String notes,
            String targetPageXmlVersion,
            List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs,
            Boolean includeXmlHistory
    ) {
        public CreateReleaseRequest(String versionTag,
                                    String notes,
                                    String targetPageXmlVersion,
                                    List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs) {
            this(versionTag, notes, targetPageXmlVersion, embeddedOutputs, true);
        }

        public boolean includeXmlHistoryResolved() {
            return includeXmlHistory == null || includeXmlHistory;
        }
    }

    public record UpsertReleaseShareRequest(
            @Future(message = "Share expiry must be in the future")
            LocalDateTime expiresAt
    ) {
    }

    public record UpdateReleaseShareRequest(
            @Future(message = "Share expiry must be in the future")
            LocalDateTime expiresAt
    ) {
    }

    public record ImportRequest(
            @NotBlank(message = "workspaceId is required")
            String workspaceId
    ) {
    }

    public enum ProjectImportAction {
        AUTO,
        REPLACE,
        RENAME,
        SKIP
    }

    public record ImportOptions(
            String previewToken,
            ProjectImportAction projectAction,
            String renamedProjectName,
            Boolean importResources,
            Map<ToolkitPackageDto.ToolkitType, ToolkitPackageDto.ImportAction> resourceActions
    ) {
        public ProjectImportAction projectActionResolved() {
            return projectAction == null ? ProjectImportAction.AUTO : projectAction;
        }

        public boolean importResourcesResolved() {
            return importResources == null || importResources;
        }

        public ToolkitPackageDto.ImportAction resourceAction(ToolkitPackageDto.ToolkitType type) {
            if (!importResourcesResolved()) {
                return ToolkitPackageDto.ImportAction.SKIP;
            }
            if (resourceActions == null) {
                return ToolkitPackageDto.ImportAction.AUTO;
            }
            return resourceActions.getOrDefault(type, ToolkitPackageDto.ImportAction.AUTO);
        }
    }

    public record ImportPreview(
            String previewToken,
            String projectName,
            String projectDescription,
            String existingProjectId,
            String suggestedProjectName,
            List<String> pageNames,
            int imageCount,
            int xmlCount,
            int xmlVersionCount,
            boolean includesXmlHistory,
            List<ToolkitPackageDto.ResourcePreview> resources,
            List<String> warnings
    ) {
    }

    public record PackageManifest(
            String schemaVersion,
            LocalDateTime exportedAt,
            String targetPageXmlVersion,
            boolean includesXmlHistory,
            ProjectSnapshot project,
            List<String> pages,
            Map<ToolkitPackageDto.ToolkitType, String> resources,
            List<String> warnings
    ) {
    }

    public record ProjectSnapshot(
            String name,
            String description,
            List<String> tags,
            boolean locked,
            String lockedReason,
            boolean allowCodecOverride,
            boolean allowDictionaryOverride,
            boolean allowVirtualKeyboardOverride,
            boolean allowLabelSetOverride,
            boolean allowTagSetOverride,
            boolean allowNormalizationProfileOverride,
            boolean allowValidationRulesetOverride,
            Integer defaultGtIndex,
            List<Integer> defaultRecognitionIndices
    ) {
    }

    public record PageDescriptor(
            String name,
            String description,
            List<String> tags,
            boolean locked,
            String lockedReason,
            Page.WorkflowState workflowState,
            ExternalSource externalSource,
            List<FileDescriptor> images,
            List<XmlFileDescriptor> xml
    ) {
    }

    public record ExternalSource(
            String type,
            String id,
            String url,
            JsonNode metadata
    ) {
    }

    public record FileDescriptor(
            String path,
            String fileName,
            String variant,
            String baseName
    ) {
    }

    public record XmlFileDescriptor(
            String path,
            String fileName,
            String variant,
            String baseName,
            List<XmlVersionDescriptor> history
    ) {
    }

    public record XmlVersionDescriptor(
            Integer versionNumber,
            String path,
            String userId,
            String comment,
            LocalDateTime created
    ) {
    }

    public record ResourceDescriptor(
            ToolkitPackageDto.ToolkitType type,
            String name,
            JsonNode payload
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
            Map<ToolkitPackageDto.ToolkitType, String> toolkitTargetIds
    ) {
    }

    public record ReleaseSummaryResponse(
            String id,
            Integer versionNumber,
            String versionTag,
            String notes,
            ProjectReleaseStatus status,
            long pageCount,
            String targetPageXmlVersion,
            boolean includeXmlHistory,
            List<DocumentExportDto.EmbeddedProjectOutputRequest> embeddedOutputs,
            String failureReason,
            String packageFileName,
            Long packageFileSize,
            String packageChecksumSha256,
            String manifestChecksumSha256,
            String createdByUserId,
            LocalDateTime sourceProjectUpdatedAt,
            boolean shareEnabled,
            String shareSecretPrefix,
            LocalDateTime shareCreatedAt,
            LocalDateTime shareExpiresAt,
            LocalDateTime shareRevokedAt,
            LocalDateTime shareLastUsedAt,
            long shareDownloadCount,
            LocalDateTime created,
            LocalDateTime updated
    ) {
    }

    public record ReleaseShareResponse(
            String downloadUrl,
            String secret,
            LocalDateTime expiresAt,
            LocalDateTime createdAt
    ) {
    }

    public enum ProjectReleaseStatus {
        CREATING,
        READY,
        FAILED
    }
}
