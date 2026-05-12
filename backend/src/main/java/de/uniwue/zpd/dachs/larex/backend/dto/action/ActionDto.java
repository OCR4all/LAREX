package de.uniwue.zpd.dachs.larex.backend.dto.action;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ExecuteRole;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.LockMode;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ActionDto {

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
            ExecuteRole executeRole,
            LockMode lockMode,
            boolean acceptsImages,
            boolean acceptsXml,
            boolean outputsImages,
            boolean outputsXml,
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
            ExecuteRole executeRole,
            LockMode lockMode,
            boolean acceptsImages,
            boolean acceptsXml,
            boolean outputsImages,
            boolean outputsXml,
            boolean enabled,
            boolean global,
            LocalDateTime created,
            LocalDateTime updated
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
            Map<String, Object> parameters
    ) {}

    public record StartRunResponse(
            RunResponse run
    ) {}

    public record RunResponse(
            String id,
            String processorDefinitionId,
            String processorKey,
            String processorName,
            String workspaceId,
            String projectId,
            List<String> pageIds,
            Status status,
            LockMode lockMode,
            int progressPercent,
            String statusMessage,
            String errorMessage,
            boolean cancelRequested,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt
    ) {}

    public record AdminRunResponse(
            String id,
            String processorDefinitionId,
            String processorKey,
            String processorName,
            String workspaceId,
            String workspaceLabel,
            String projectId,
            String projectLabel,
            int pageCount,
            Status status,
            int progressPercent,
            String statusMessage,
            String errorMessage,
            boolean cancelRequested,
            String logText,
            List<ActionRunLogEventResponse> logEvents,
            Object resultSummary,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime created,
            LocalDateTime updated,
            LocalDateTime completedAt,
            Long durationSeconds
    ) {}

    public record ClearRunsResponse(
            int deletedCount
    ) {}

    public record HealthCheckResponse(
            boolean ok,
            int statusCode,
            String url,
            String message,
            long durationMillis
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
            List<MachinePageFile> images,
            List<MachinePageFile> xml
    ) {}

    public record MachineInputResponse(
            int protocolVersion,
            String runId,
            String processorKey,
            String projectId,
            Map<String, Object> parameters,
            List<MachinePageInput> pages,
            boolean cancelRequested
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
            List<ResultFile> files
    ) {}

    public record ResultFile(
            String fieldName,
            String pageId,
            String type,
            String variant,
            String fileName
    ) {}
}
