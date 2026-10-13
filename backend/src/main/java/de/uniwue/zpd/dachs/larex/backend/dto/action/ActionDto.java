package de.uniwue.zpd.dachs.larex.backend.dto.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionKind;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Kind;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ActionDto {

    public enum AnnotationLevel {
        REGIONS, TEXT_LINES, BASELINES, TEXT, WORDS, GLYPHS, READING_ORDER
    }

    public enum WarningPrecision { DECLARED, UNKNOWN }

    public record ImpactPage(String pageId, String name, TargetSelectionPage targetSelection,
                             List<AnnotationLevel> affectedLevels, WarningPrecision warningPrecision,
                             boolean affected) {}

    public record SkippedImpactPage(String pageId, String name, String reason) {}

    public record RunImpactResponse(ActionTarget target, List<ImpactPage> pages,
                                    List<SkippedImpactPage> skippedPages) {}

    public record RetryRunRequest(List<String> excludedPageIds) {}

    public enum InputLevel {
        NONE,
        OPTIONAL,
        REQUIRED
    }

    public record InputRequirement(
            InputLevel level,
            List<ActionTarget> requiredForTargets,
            Boolean multiple
    ) {
        public InputRequirement {
            multiple = Boolean.TRUE.equals(multiple);
        }

        public InputRequirement(InputLevel level, List<ActionTarget> requiredForTargets) {
            this(level, requiredForTargets, false);
        }

        public void validateImageCount(long count) {
            if (!multiple && count > 1) {
                throw new IllegalArgumentException("This Action does not accept multiple image variants per page or dataset item");
            }
        }

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

    public enum EvaluationOutputMode { REPORT, FILES }

    public record EvaluationDefinition(
            String profile,
            int profileVersion,
            TrainingSplitRequirements splits,
            EvaluationOutputMode outputMode
    ) {
        public EvaluationDefinition(String profile, int profileVersion, TrainingSplitRequirements splits) {
            this(profile, profileVersion, splits, EvaluationOutputMode.REPORT);
        }
    }

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
            String info,
            String endpointUrl,
            int endpointTimeoutSeconds,
            ActionKind kind,
            ExecuteRole executeRole,
            LockMode lockMode,
            List<String> tags,
            List<ActionTarget> targets,
            InputRequirements inputs,
            boolean acceptsImages,
            boolean acceptsXml,
            boolean outputsImages,
            boolean outputsXml,
            boolean outputsFiles,
            TrainingSplitRequirements trainingSplits,
            EvaluationDefinition evaluation,
            Map<String, ActionDefinitionDocument.Parameter> parameters,
            Map<ActionTarget, List<AnnotationLevel>> overwrites
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
            String info,
            String yaml,
            String endpointUrl,
            int endpointTimeoutSeconds,
            ActionKind kind,
            ExecuteRole executeRole,
            LockMode lockMode,
            List<String> tags,
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
            Map<String, ActionDefinitionDocument.Parameter> parameters,
            Map<ActionTarget, List<AnnotationLevel>> overwrites
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

    public enum ActivationScope { WORKSPACE, PROJECTS }

    public record ActivationRequest(
            @NotNull ActivationScope scope,
            @NotNull List<@NotBlank String> projectIds,
            @NotNull Boolean enabled
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

    // Singular fields remain readable for existing clients and persisted runs.
    public record ImageVariantSelection(
            String mode,
            String variant,
            Map<String, String> pageVariants,
            Boolean fallbackImage,
            List<String> variants,
            Map<String, List<String>> pageVariantLists
    ) {
        public ImageVariantSelection(String mode, String variant, Map<String, String> pageVariants, Boolean fallbackImage) {
            this(mode, variant, pageVariants, fallbackImage, null, null);
        }
    }

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

    // The list fields take precedence over the legacy single-image selections.
    public record TrainingImageSelection(
            String globalVariant,
            Map<String, String> itemImageIds,
            List<String> globalVariants,
            Map<String, List<String>> itemImageIdLists
    ) {
        public TrainingImageSelection(String globalVariant, Map<String, String> itemImageIds) {
            this(globalVariant, itemImageIds, null, null);
        }
    }

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
            String createdByUserId,
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
            LocalDateTime completedAt,
            List<String> settledPageIds,
            ReportCounts reportCounts,
            int retryablePageCount
    ) {}

    public record RunDetailResponse(
            RunResponse run,
            String logText,
            List<ActionRunLogEventResponse> logEvents,
            Object resultSummary,
            Long durationSeconds,
            Object evaluationReport,
            RunReport runReport,
            EvaluationOutputMode evaluationOutputMode,
            ActionOutputDto.OutputResponse evaluationOutput
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
            Object evaluationReport,
            ReportCounts reportCounts
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
            boolean cancelRequested,
            EvaluationOutputMode evaluationOutputMode
    ) {
        public MachineInputResponse(int protocolVersion, String runId, String processorKey, Kind kind,
                String projectId, String datasetId, Map<String, Object> parameters, List<MachinePageInput> pages,
                MachineTargetSelection targetSelection, InputRequirements inputRequirements,
                ImageVariantSelection imageVariantSelection, MachineCapabilities capabilities, boolean cancelRequested) {
            this(protocolVersion, runId, processorKey, kind, projectId, datasetId, parameters, pages,
                    targetSelection, inputRequirements, imageVariantSelection, capabilities, cancelRequested, null);
        }
    }

    public record MachineCapabilities(
            boolean incrementalPageResults,
            boolean customFileResults,
            boolean evaluationReports,
            boolean runReports
    ) {
        public MachineCapabilities(boolean incrementalPageResults, boolean customFileResults, boolean evaluationReports) {
            this(incrementalPageResults, customFileResults, evaluationReports, true);
        }
    }

    public record HeartbeatRequest(
            Integer progressPercent,
            String statusMessage,
            String log,
            String logLevel,
            String status,
            String errorMessage,
            RunReport runReport
    ) {
        public HeartbeatRequest(Integer progressPercent, String statusMessage, String log, String logLevel,
                                String status, String errorMessage) {
            this(progressPercent, statusMessage, log, logLevel, status, errorMessage, null);
        }
    }

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
            Object evaluationReport,
            PageOutcome pageOutcome,
            RunReport runReport
    ) {
        public ResultManifest(Integer protocolVersion, String status, String message, String pageId,
                              List<ResultFile> files, List<ResultPatch> patches, Object evaluationReport) {
            this(protocolVersion, status, message, pageId, files, patches, evaluationReport, null, null);
        }

        public ResultManifest(Integer protocolVersion, String status, String message, String pageId,
                              List<ResultFile> files, List<ResultPatch> patches) {
            this(protocolVersion, status, message, pageId, files, patches, null);
        }
    }

    public enum PageOutcomeStatus { SUCCEEDED, FAILED, SKIPPED, UNFINISHED, UNKNOWN }

    public record PageOutcome(String pageId, String pageName, PageOutcomeStatus status,
                              String message, List<String> warnings) {
        public PageOutcome {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record RunIssue(String severity, String message) {}

    public record ReportCounts(int total, int succeeded, int failed, int skipped, int unfinished, int unknown) {}

    public record RunReport(Integer schemaVersion, List<PageOutcome> pageOutcomes,
                            List<RunIssue> issues, ReportCounts counts) {
        public RunReport {
            schemaVersion = schemaVersion == null ? 1 : schemaVersion;
            pageOutcomes = pageOutcomes == null ? List.of() : List.copyOf(pageOutcomes);
            issues = issues == null ? List.of() : List.copyOf(issues);
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
