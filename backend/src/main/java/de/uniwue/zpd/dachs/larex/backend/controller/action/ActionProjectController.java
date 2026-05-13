package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/actions")
public class ActionProjectController {

    private final ActionRunService actionRunService;

    @Value("${larex.actions.public-base-url:}")
    private String configuredPublicBaseUrl;

    public ActionProjectController(ActionRunService actionRunService) {
        this.actionRunService = actionRunService;
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
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listExecutableProcessors(workspaceId, projectId, userId));
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
                publicApiBaseUrl(httpRequest)
        ));
    }

    @GetMapping("/projects/{projectId}/runs")
    public ResponseEntity<List<ActionDto.RunResponse>> listRuns(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.listRuns(workspaceId, projectId, userId));
    }

    @GetMapping("/projects/{projectId}/runs/{runId}")
    public ResponseEntity<ActionDto.RunDetailResponse> getRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.getRunDetail(workspaceId, projectId, runId, userId));
    }

    @PostMapping("/projects/{projectId}/runs/{runId}/retry")
    public ResponseEntity<ActionDto.StartRunResponse> retryRun(
            @PathVariable String workspaceId,
            @PathVariable String projectId,
            @PathVariable String runId,
            @AuthenticationPrincipal(expression = "subject") String userId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(actionRunService.retryRun(
                workspaceId,
                projectId,
                runId,
                userId,
                publicApiBaseUrl(httpRequest)
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

    private String publicApiBaseUrl(HttpServletRequest request) {
        if (configuredPublicBaseUrl != null && !configuredPublicBaseUrl.isBlank()) {
            return configuredPublicBaseUrl.replaceAll("/+$", "");
        }
        String root = ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(request.getContextPath())
                .replaceQuery(null)
                .build()
                .toUriString();
        return root + "/api/v1";
    }
}
