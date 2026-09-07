package de.uniwue.zpd.dachs.larex.backend.dto.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionCategory;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionKind;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Kind;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ActionDto {

    public enum InputLevel {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    public record InputRequirement(
            InputLevel level,
            List<ActionTarget> requiredForTargets
    ) {
        public InputLevel levelFor(ActionTarget target) {
            return requiredForTargets != null && requiredForTargets.contains(target)
                    ? InputLevel.REQUIRED
                    : level;
        }

        public boolean accepts(ActionTarget target) {
            return levelFor(target) != InputLevel.NONE;
        }

        public boolean required(ActionTarget target) {
            return levelFor(target) == InputLevel.REQUIRED;
        }

        public boolean acceptsAnyTarget() {
            return level != InputLevel.NONE || (requiredForTargets != null && !requiredForTargets.isEmpty());
        }
    }

    public record InputRequirements(
            InputRequirement images,
            InputRequirement xml
    ) {}

    public record TrainingSplitRequirements(
            InputLevel train,
            InputLevel val,
            InputLevel test
    ) {}

    public record EvaluationDefinition(
            String profile,
            int profileVersion,
            TrainingSplitRequirements splits
    ) {}

    public record ValidationDiagnostic(
            String severity,
            String path,
            Integer line,
            Integer column,
            String message
    ) {}

    public record ValidationResponse(
            boolean valid,
            List<ValidationDiagnostic> diagnostics,
            DefinitionPreview preview
    ) {}

    public record DefinitionPreview(
            String processorKey,
            String name,
            String description,
            String endpointUrl,
            int endpointTimeoutSeconds,
            ActionKind kind,
            ExecuteRole executeRole,
            LockMode lockMode,
            ActionCategory category,
            List<ActionTarget> targets,
            InputRequirements inputs,
            boolean acceptsImages,
            boolean acceptsXml,
            boolean outputsImages,
            boolean outputsXml,
            boolean outputsFiles,
            TrainingSplitRequirements trainingSplits,
            EvaluationDefinition evaluation,
            Map<String, ActionDefinitionDocument.Parameter> parameters
    ) {}

    public record DefinitionRequest(
            @NotBlank String yaml,
            Boolean enabled
    ) {}

    public record DefinitionResponse(
            String id,
            String processorKey,
            String name,
            String description,
            String yaml,
            String endpointUrl,
            int endpointTimeoutSeconds,
            ActionKind kind,
            ExecuteRole executeRole,
            LockMode lockMode,
            ActionCategory category,
            List<ActionTarget> targets,
            InputRequirements inputs,
            boolean acceptsImages,
            boolean acceptsXml,
            boolean outputsImages,
            boolean outputsXml,
            boolean outputsFiles,
            boolean enabled,
            boolean global,
            LocalDateTime created,
            LocalDateTime updated,
            TrainingSplitRequirements trainingSplits,
            EvaluationDefinition evaluation,
            Map<String, ActionDefinitionDocument.Parameter> parameters
    ) {}

    public record WorkspaceAvailabilityRequest(
            @NotBlank String workspaceId,
            Boolean enabled
    ) {}

    public record WorkspaceAvailabilityResponse(
            String id,
            String workspaceId,
            boolean enabled,
            DefinitionResponse processor,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record AssignmentRequest(
            @NotBlank String processorDefinitionId,
            String projectId,
            Boolean enabled
    ) {}

    public record AssignmentResponse(
            String id,
            String workspaceId,
            String projectId,
            boolean enabled,
            DefinitionResponse processor
    ) {}

    public record ExecutableProcessorResponse(
            String assignmentId,
            DefinitionResponse processor,
            boolean executable,
            String blockedReason
    ) {}

    public record StartRunRequest(
            @NotBlank String processorDefinitionId,
            List<String> pageIds,
            Map<String, Object> parameters,
            TargetSelection targetSelection,
            ImageVariantSelection imageVariantSelection,
            Boolean enqueueIfBusy
    ) {}

    public record ImageVariantSelection(
            String mode,
            String variant,
            Map<String, String> pageVariants,
            Boolean fallbackImage
    ) {}

    public record TargetSelection(
            ActionTarget type,
            List<TargetSelectionPage> pages
    ) {}

    public record TargetSelectionPage(
            String pageId,
            List<String> regionIds,
            List<String> textLineIds
    ) {}

    public record StartRunResponse(
            RunResponse run
    ) {}

    public record StartTrainingRunRequest(
            @NotBlank String processorDefinitionId,
            TrainingSelection selection,
            TrainingImageSelection imageSelection,
            Map<String, Object> parameters,
            Boolean enqueueIfBusy
    ) {}

    /** Dataset-scoped evaluation uses the same selection/image contract as training. */
    public record StartEvaluationRunRequest(
            @NotBlank String processorDefinitionId,
            TrainingSelection selection,
            TrainingImageSelection imageSelection,
            Map<String, Object> parameters,
            Boolean enqueueIfBusy
    ) {}

    public record TrainingSelection(
            String mode,
            List<String> itemIds
    ) {}

    public record TrainingImageSelection(
            String globalVariant,
            Map<String, String> itemImageIds
    ) {}

    public record TrainingInputResponse(
            String datasetId,
            String datasetName,
            List<TrainingInputItem> items
    ) {}

    public record TrainingInputItem(
            String itemId,
            String sourcePageId,
            String pageName,
            DatasetItem.Split split,
            DatasetItem.Status status,
            String brokenReason,
            boolean xmlAvailable,
            List<TrainingImageChoice> images
    ) {}

    public record TrainingImageChoice(
            String id,
            String fileName,
            String variant
    ) {}

    public record ParameterValuesResponse(
            Map<String, List<ActionDefinitionDocument.ParameterChoice>> values
    ) {}

    public record RunResponse(
            String id,
            String processorDefinitionId,
            String processorKey,
            String processorName,
            Kind kind,
            String workspaceId,
            String projectId,
            String projectLabel,
            String datasetId,
            String datasetLabel,
            int inputCount,
            Map<String, Long> splitCounts,
            int pageCount,
            List<String> pageIds,
            List<String> completedPageIds,
            TargetSelection targetSelection,
            Status status,
            LockMode lockMode,
            int progressPercent,
            Integer queuePosition,
            String statusMessage,
            String errorMessage,
            boolean canCancel,
            boolean cancelRequested,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}

    public record RunDetailResponse(
            RunResponse run,
            String logText,
            List<ActionRunLogEventResponse> logEvents,
            Object resultSummary,
            Long durationSeconds,
            Object evaluationReport
    ) {}

    public record EvaluationMetricComparison(
            String key,
            String label,
            Object currentValue,
            Object baselineValue,
            double delta,
            String format,
            String unit,
            String direction,
            String state
    ) {}

    public record EvaluationComparisonResponse(
            String currentRunId,
            String baselineRunId,
            List<EvaluationMetricComparison> metrics,
            Object currentReport,
            Object baselineReport
    ) {}

    public record AdminRunResponse(
            String id,
            String processorDefinitionId,
            String processorKey,
            String processorName,
            Kind kind,
            String workspaceId,
            String workspaceLabel,
            String projectId,
            String projectLabel,
            String datasetId,
            String datasetLabel,
            int inputCount,
            Map<String, Long> splitCounts,
            int pageCount,
            Status status,
            int progressPercent,
            Integer queuePosition,
            String statusMessage,
            String errorMessage,
            boolean canCancel,
            boolean cancelRequested,
            String logText,
            List<ActionRunLogEventResponse> logEvents,
            Object resultSummary,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt,
            Long durationSeconds,
            Object evaluationReport
    ) {}

    public record ClearRunsResponse(
            int deletedCount
    ) {}

    public record BulkCancelRunsResponse(
            int cancelledCount
    ) {}

    public record HealthCheckResponse(
            boolean ok,
            int statusCode,
            String url,
            String message,
            long durationMillis
    ) {}

    public record AuditEventResponse(
            String id,
            String action,
            String outcome,
            String actorUserId,
            String processorDefinitionId,
            String runId,
            String workspaceId,
            String projectId,
            Object details,
            LocalDateTime created
    ) {}

    public record EndpointSecretRequest(
            @NotBlank String ref,
            String displayName,
            String description
    ) {}

    public record EndpointSecretResponse(
            String id,
            String ref,
            String envName,
            String displayName,
            String description,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime lastUsedAt,
            LocalDateTime rotatedAt,
            String source
    ) {}

    public record EndpointSecretRevealResponse(
            EndpointSecretResponse secret,
            String plaintext
    ) {}

    public record ActionRunLogEventResponse(
            String id,
            String level,
            String message,
            LocalDateTime created
    ) {}

    public record MachinePageFile(
            String id,
            String fileName,
            String variant,
            String mimeType,
            Long fileSize,
            String downloadUrl
    ) {}

    public record MachinePageInput(
            String id,
            String name,
            String sourcePageId,
            DatasetItem.Split split,
            List<MachinePageFile> images,
            List<MachinePageFile> xml
    ) {}

    public record MachineTargetSelection(
            ActionTarget type,
            List<MachineTargetPage> pages
    ) {}

    public record MachineTargetPage(
            String pageId,
            List<String> regionIds,
            List<String> textLineIds
    ) {}

    public record MachineInputResponse(
            int protocolVersion,
            String runId,
            String processorKey,
            Kind kind,
            String projectId,
            String datasetId,
            Map<String, Object> parameters,
            List<MachinePageInput> pages,
            MachineTargetSelection targetSelection,
            InputRequirements inputRequirements,
            ImageVariantSelection imageVariantSelection,
            MachineCapabilities capabilities,
            boolean cancelRequested
    ) {}

    public record MachineCapabilities(
            boolean incrementalPageResults,
            boolean customFileResults,
            boolean evaluationReports
    ) {}

    public record HeartbeatRequest(
            Integer progressPercent,
            String statusMessage,
            String log,
            String logLevel,
            String status,
            String errorMessage
    ) {}

    public record HeartbeatResponse(
            boolean cancelRequested
    ) {}

    public record ResultManifest(
            Integer protocolVersion,
            String status,
            String message,
            String pageId,
            List<ResultFile> files,
            List<ResultPatch> patches,
            Object evaluationReport
    ) {
        public ResultManifest(Integer protocolVersion, String status, String message, String pageId,
                              List<ResultFile> files, List<ResultPatch> patches) {
            this(protocolVersion, status, message, pageId, files, patches, null);
        }
    }

    public record ResultFile(
            String fieldName,
            String pageId,
            String type,
            String variant,
            String fileName
    ) {}

    public record ResultPatch(
            String type,
            String pageId,
            String regionId,
            String textLineId,
            String text,
            Double confidence,
            Integer index,
            String fieldName,
            String fileName
    ) {}
}
