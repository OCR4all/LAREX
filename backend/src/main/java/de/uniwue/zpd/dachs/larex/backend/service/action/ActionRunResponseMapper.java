package de.uniwue.zpd.dachs.larex.backend.service.action;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun.Status;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRunLogEvent;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunLogEventRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionRunRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ActionRunResponseMapper {

    private final ActionRunRepository runRepository;
    private final ActionRunLogEventRepository logEventRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final GlobalAdminService globalAdminService;
    private final ActionDefinitionService definitionService;
    private final ActionRunPayloadService payloadService;
    private final ObjectMapper objectMapper;

    public ActionRunResponseMapper(ActionRunRepository runRepository,
                                   ActionRunLogEventRepository logEventRepository,
                                   ProjectRepository projectRepository,
                                   WorkspaceAccessService workspaceAccessService,
                                   GlobalAdminService globalAdminService,
                                   ActionDefinitionService definitionService,
                                   ActionRunPayloadService payloadService,
                                   ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.logEventRepository = logEventRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.globalAdminService = globalAdminService;
        this.definitionService = definitionService;
        this.payloadService = payloadService;
        this.objectMapper = objectMapper;
    }

    public ActionDto.RunResponse toRunResponse(ActionRun run) {
        return toRunResponse(run, resolveProjectLabel(run.getProjectId()), null);
    }

    public ActionDto.RunResponse toRunResponse(ActionRun run, String projectLabel, String userId) {
        return toRunResponse(run, projectLabel, userId, Map.of());
    }

    public ActionDto.RunResponse toRunResponse(
            ActionRun run,
            String projectLabel,
            String userId,
            Map<String, Integer> queuePositions
    ) {
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        List<String> pageIds = payloadService.readPageIds(run);
        return new ActionDto.RunResponse(
                run.getId(),
                definition.getId(),
                definition.getProcessorKey(),
                definition.getName(),
                run.getWorkspaceId(),
                run.getProjectId(),
                projectLabel,
                pageIds.size(),
                pageIds,
                payloadService.readTargetSelection(run),
                run.getStatus(),
                run.getLockMode(),
                run.getProgressPercent(),
                queuePosition(run, queuePositions),
                run.getStatusMessage(),
                run.getErrorMessage(),
                canCancelRun(run.getWorkspaceId(), run, userId),
                run.isCancelRequested(),
                run.getLastHeartbeatAt(),
                run.getCreated(),
                run.getUpdated(),
                run.getCompletedAt()
        );
    }

    public ActionDto.RunDetailResponse toRunDetailResponse(ActionRun run, String projectLabel, String userId) {
        return new ActionDto.RunDetailResponse(
                toRunResponse(run, projectLabel, userId),
                run.getLogText(),
                logEventRepository.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                        .map(this::toLogEventResponse)
                        .toList(),
                readResultSummary(run.getResultSummaryJson()),
                durationSeconds(run)
        );
    }

    public ActionDto.AdminRunResponse toAdminRunResponse(ActionRun run) {
        return toAdminRunResponse(run, Map.of());
    }

    public ActionDto.AdminRunResponse toAdminRunResponse(ActionRun run, Map<String, Integer> queuePositions) {
        ActionProcessorDefinition definition = run.getProcessorDefinition();
        Project project = projectRepository.findById(run.getProjectId()).orElse(null);
        return new ActionDto.AdminRunResponse(
                run.getId(),
                definition.getId(),
                definition.getProcessorKey(),
                definition.getName(),
                run.getWorkspaceId(),
                run.getWorkspaceId(),
                run.getProjectId(),
                project == null ? run.getProjectId() : project.getName(),
                payloadService.readPageIds(run).size(),
                run.getStatus(),
                run.getProgressPercent(),
                queuePosition(run, queuePositions),
                run.getStatusMessage(),
                run.getErrorMessage(),
                globalAdminService.isGlobalAdmin(),
                run.isCancelRequested(),
                run.getLogText(),
                logEventRepository.findByRunIdOrderByCreatedAsc(run.getId()).stream()
                        .map(this::toLogEventResponse)
                        .toList(),
                readResultSummary(run.getResultSummaryJson()),
                run.getLastHeartbeatAt(),
                run.getCreated(),
                run.getUpdated(),
                run.getCompletedAt(),
                durationSeconds(run)
        );
    }

    public Map<String, Integer> queuePositionsByRunId(List<ActionRun> runs) {
        Set<String> definitionIds = runs.stream()
                .filter(run -> run.getStatus() == Status.QUEUED)
                .map(run -> run.getProcessorDefinition().getId())
                .collect(Collectors.toSet());
        if (definitionIds.isEmpty()) {
            return Map.of();
        }

        Map<String, Integer> positions = new HashMap<>();
        for (String definitionId : definitionIds) {
            List<ActionRun> queuedRuns = runRepository.findByProcessorDefinitionIdAndStatusOrderByCreatedAsc(definitionId, Status.QUEUED);
            if (queuedRuns.isEmpty()) {
                continue;
            }
            String scope = concurrencyScope(queuedRuns.getFirst().getProcessorDefinition());
            Map<String, Integer> perScopeCounters = new HashMap<>();
            for (ActionRun queuedRun : queuedRuns) {
                String scopeKey = queueScopeKey(scope, queuedRun);
                int position = perScopeCounters.merge(scopeKey, 1, Integer::sum);
                positions.put(queuedRun.getId(), position);
            }
        }
        return positions;
    }

    private ActionDto.ActionRunLogEventResponse toLogEventResponse(ActionRunLogEvent event) {
        return new ActionDto.ActionRunLogEventResponse(
                event.getId(),
                event.getLevel(),
                event.getMessage(),
                event.getCreated()
        );
    }

    private Object readResultSummary(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JacksonException e) {
            return json;
        }
    }

    private Long durationSeconds(ActionRun run) {
        if (run.getCreated() == null) {
            return null;
        }
        LocalDateTime end = run.getCompletedAt() == null ? run.getUpdated() : run.getCompletedAt();
        return end == null ? null : ChronoUnit.SECONDS.between(run.getCreated(), end);
    }

    private Integer queuePosition(ActionRun run, Map<String, Integer> queuePositions) {
        if (run.getStatus() != Status.QUEUED) {
            return null;
        }
        Integer precomputed = queuePositions.get(run.getId());
        if (precomputed != null) {
            return precomputed;
        }

        ActionProcessorDefinition definition = run.getProcessorDefinition();
        String scope = concurrencyScope(definition);
        int position = 0;
        for (ActionRun queuedRun : runRepository.findByProcessorDefinitionIdAndStatusOrderByCreatedAsc(definition.getId(), Status.QUEUED)) {
            if (!sameConcurrencyScope(run, queuedRun, scope)) {
                continue;
            }
            position += 1;
            if (run.getId().equals(queuedRun.getId())) {
                return position;
            }
        }
        return null;
    }

    private String queueScopeKey(String scope, ActionRun run) {
        return switch (scope) {
            case "GLOBAL" -> "GLOBAL";
            case "WORKSPACE" -> run.getWorkspaceId();
            default -> run.getWorkspaceId() + "::" + run.getProjectId();
        };
    }

    private String concurrencyScope(ActionProcessorDefinition definition) {
        ActionDefinitionDocument.Concurrency concurrency = definitionService.readParsedDocument(definition).concurrency();
        return concurrency == null || concurrency.scope() == null || concurrency.scope().isBlank()
                ? "PROJECT"
                : concurrency.scope().trim().toUpperCase(Locale.ROOT);
    }

    private boolean sameConcurrencyScope(ActionRun left, ActionRun right, String scope) {
        if (!Objects.equals(left.getProcessorDefinition().getId(), right.getProcessorDefinition().getId())) {
            return false;
        }
        return switch (scope) {
            case "GLOBAL" -> true;
            case "WORKSPACE" -> Objects.equals(left.getWorkspaceId(), right.getWorkspaceId());
            default -> Objects.equals(left.getWorkspaceId(), right.getWorkspaceId())
                    && Objects.equals(left.getProjectId(), right.getProjectId());
        };
    }

    private boolean canCancelRun(String workspaceId, ActionRun run, String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (globalAdminService.isGlobalAdmin()) {
            return true;
        }
        if (workspaceAccessService.canManageProjects(workspaceId, userId)) {
            return true;
        }
        return workspaceAccessService.hasWorkspaceAccess(workspaceId, userId)
                && userId.equals(run.getCreatedByUserId());
    }

    private String resolveProjectLabel(String projectId) {
        return projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse(projectId);
    }
}
