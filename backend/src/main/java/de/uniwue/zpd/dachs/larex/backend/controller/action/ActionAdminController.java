package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/actions/processors")
public class ActionAdminController {

    private final ActionDefinitionService definitionService;
    private final ActionRunService actionRunService;

    public ActionAdminController(ActionDefinitionService definitionService,
                                 ActionRunService actionRunService) {
        this.definitionService = definitionService;
        this.actionRunService = actionRunService;
    }

    @GetMapping
    public ResponseEntity<List<ActionDto.DefinitionResponse>> listDefinitions() {
        return ResponseEntity.ok(definitionService.listDefinitions());
    }

    @GetMapping("/{definitionId}")
    public ResponseEntity<ActionDto.DefinitionResponse> getDefinition(@PathVariable String definitionId) {
        return ResponseEntity.ok(definitionService.getDefinition(definitionId));
    }

    @PostMapping("/validate")
    public ResponseEntity<ActionDto.ValidationResponse> validateDefinition(
            @RequestBody ActionDto.DefinitionRequest request,
            @RequestParam(required = false) String existingDefinitionId) {
        return ResponseEntity.ok(definitionService.validateYaml(request.yaml(), existingDefinitionId));
    }

    @PostMapping
    public ResponseEntity<ActionDto.DefinitionResponse> createDefinition(
            @Valid @RequestBody ActionDto.DefinitionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(definitionService.createDefinition(request, userId));
    }

    @PutMapping("/{definitionId}")
    public ResponseEntity<ActionDto.DefinitionResponse> updateDefinition(
            @PathVariable String definitionId,
            @Valid @RequestBody ActionDto.DefinitionRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(definitionService.updateDefinition(definitionId, request, userId));
    }

    @PutMapping("/{definitionId}/enabled")
    public ResponseEntity<ActionDto.DefinitionResponse> setEnabled(
            @PathVariable String definitionId,
            @RequestParam boolean enabled,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(definitionService.setEnabled(definitionId, enabled, userId));
    }

    @DeleteMapping("/{definitionId}")
    public ResponseEntity<Void> deleteDefinition(@PathVariable String definitionId) {
        definitionService.deleteDefinition(definitionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{definitionId}/test-endpoint")
    public ResponseEntity<ActionDto.HealthCheckResponse> testEndpoint(@PathVariable String definitionId) {
        return ResponseEntity.ok(definitionService.testEndpoint(definitionId));
    }

    @GetMapping("/{definitionId}/audit")
    public ResponseEntity<List<ActionDto.AuditEventResponse>> listAuditEvents(@PathVariable String definitionId) {
        return ResponseEntity.ok(definitionService.listAuditEvents(definitionId));
    }

    @PutMapping("/{definitionId}/global")
    public ResponseEntity<ActionDto.DefinitionResponse> setGlobalAvailable(
            @PathVariable String definitionId,
            @RequestParam boolean global,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(definitionService.setGlobalAvailable(definitionId, global, userId));
    }

    @GetMapping("/{definitionId}/workspace-availability")
    public ResponseEntity<List<ActionDto.WorkspaceAvailabilityResponse>> listWorkspaceAvailability(
            @PathVariable String definitionId) {
        return ResponseEntity.ok(definitionService.listWorkspaceAvailability(definitionId));
    }

    @PostMapping("/{definitionId}/workspace-availability")
    public ResponseEntity<ActionDto.WorkspaceAvailabilityResponse> assignWorkspaceAvailability(
            @PathVariable String definitionId,
            @Valid @RequestBody ActionDto.WorkspaceAvailabilityRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(definitionService.assignWorkspaceAvailability(definitionId, request, userId));
    }

    @DeleteMapping("/{definitionId}/workspace-availability/{availabilityId}")
    public ResponseEntity<Void> removeWorkspaceAvailability(
            @PathVariable String definitionId,
            @PathVariable String availabilityId) {
        definitionService.removeWorkspaceAvailability(definitionId, availabilityId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{definitionId}/runs")
    public ResponseEntity<List<ActionDto.AdminRunResponse>> listRuns(
            @PathVariable String definitionId,
            @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(actionRunService.listAdminRuns(definitionId, limit));
    }

    @GetMapping("/{definitionId}/runs/{runId}")
    public ResponseEntity<ActionDto.AdminRunResponse> getRun(
            @PathVariable String definitionId,
            @PathVariable String runId) {
        return ResponseEntity.ok(actionRunService.getAdminRun(definitionId, runId));
    }

    @DeleteMapping("/{definitionId}/runs/terminal")
    public ResponseEntity<ActionDto.ClearRunsResponse> clearTerminalRuns(
            @PathVariable String definitionId) {
        return ResponseEntity.ok(actionRunService.clearTerminalAdminRuns(definitionId));
    }

    @PostMapping("/{definitionId}/runs/cancel-active")
    public ResponseEntity<ActionDto.BulkCancelRunsResponse> cancelActiveRuns(
            @PathVariable String definitionId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(actionRunService.cancelActiveAdminRuns(definitionId, userId));
    }
}
