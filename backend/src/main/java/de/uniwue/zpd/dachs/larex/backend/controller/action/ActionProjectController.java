package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition.ActionTarget;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionPublicBaseUrlService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/actions")
public class ActionProjectController {

    private final ActionRunService actionRunService;
    private final ActionPublicBaseUrlService publicBaseUrlService;

    public ActionProjectController(ActionRunService actionRunService,
                                   ActionPublicBaseUrlService publicBaseUrlService) {
        this.actionRunService = actionRunService;
        this.publicBaseUrlService = publicBaseUrlService;
    }

    @GetMapping("/processors/available")
    public ResponseEntity<List<ActionDto.DefinitionResponse>> listAvailableDefinitions(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listAvailableDefinitions(workspaceId, userId));
    }

    @GetMapping("/assignments")
    public ResponseEntity<List<ActionDto.AssignmentResponse>> listAssignments(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listAssignments(workspaceId, projectId, userId));
    }

    @PostMapping("/assignments")
    public ResponseEntity<ActionDto.AssignmentResponse> assignProcessor(
            @PathVariable String workspaceId,
            @Valid @RequestBody ActionDto.AssignmentRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.assignProcessor(workspaceId, request, userId));
    }

    @DeleteMapping("/assignments/{assignmentId}")
    public ResponseEntity<Void> unassignProcessor(
            @PathVariable String workspaceId,
            @PathVariable String assignmentId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        actionRunService.unassignProcessor(workspaceId, assignmentId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/processors")
    public ResponseEntity<List<ActionDto.ExecutableProcessorResponse>> listExecutableProcessors(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam(required = false) ActionTarget target,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listExecutableProcessors(workspaceId, projectId, userId, target));
    }

    @GetMapping("/projects/{projectId}/processors/{definitionId}/parameter-values")
    public ResponseEntity<ActionDto.ParameterValuesResponse> discoverParameterValues(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String definitionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.discoverParameterValues(
                workspaceId, projectId, definitionId, userId));
    }

    @PostMapping("/projects/{projectId}/runs")
    public ResponseEntity<ActionDto.StartRunResponse> startRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @Valid @RequestBody ActionDto.StartRunRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(actionRunService.startRun(
                workspaceId,
                projectId,
                request,
                userId,
                publicBaseUrlService.publicApiBaseUrl(httpRequest)
        ));
    }

    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<List<ActionDto.RunResponse>> listRuns(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @RequestParam(defaultValue = "200") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listRuns(workspaceId, projectId, userId, limit));
    }

    @GetMapping("/runs")
    public ResponseEntity<List<ActionDto.RunResponse>> listWorkspaceRuns(
            @PathVariable String workspaceId,
            @RequestParam(defaultValue = "200") int limit,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listWorkspaceRuns(workspaceId, userId, limit));
    }

    @PostMapping("/runs/history/dismiss")
    public ResponseEntity<ActionDto.ClearRunsResponse> dismissWorkspaceRunHistory(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.dismissWorkspaceRunHistory(workspaceId, userId));
    }

    @DeleteMapping("/projects/{projectId}/runs/history")
    public ResponseEntity<ActionDto.ClearRunsResponse> clearRunHistory(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.clearProjectRunHistory(workspaceId, projectId, userId));
    }

    @PostMapping("/projects/{projectId}/runs/history/dismiss")
    public ResponseEntity<ActionDto.ClearRunsResponse> dismissRunHistory(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.dismissProjectRunHistory(workspaceId, projectId, userId));
    }

    @GetMapping("/projects/{projectId}/runs/{runId}")
    public ResponseEntity<ActionDto.RunDetailResponse> getRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.getRunDetail(workspaceId, projectId, runId, userId));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ActionDto.RunDetailResponse> getWorkspaceRun(
            @PathVariable String workspaceId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.getWorkspaceRunDetail(workspaceId, runId, userId));
    }

    @PostMapping("/projects/{projectId}/runs/{runId}/retry")
    public ResponseEntity<ActionDto.StartRunResponse> retryRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @RequestParam(defaultValue = "false") boolean enqueueIfBusy,
            @AuthenticationPrincipal(expression = "subject") String userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(actionRunService.retryRun(
                workspaceId,
                projectId,
                runId,
                enqueueIfBusy,
                userId,
                publicBaseUrlService.publicApiBaseUrl(httpRequest)
        ));
    }

    @PostMapping("/projects/{projectId}/runs/{runId}/cancel")
    public ResponseEntity<ActionDto.RunResponse> cancelRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.cancelRun(workspaceId, projectId, runId, userId));
    }

    @PostMapping("/projects/{projectId}/runs/{runId}/dismiss")
    public ResponseEntity<Void> dismissRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        actionRunService.dismissRun(workspaceId, projectId, runId, userId);
        return ResponseEntity.noContent().build();
    }

}
