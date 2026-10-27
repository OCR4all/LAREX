package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionWorkflowDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionPublicBaseUrlService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionWorkflowService;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/actions")
public class ActionWorkflowController {
    private final ActionWorkflowService workflows;
    private final ActionPublicBaseUrlService publicBaseUrl;
    private final ActionRunService actions;
    public ActionWorkflowController(ActionWorkflowService workflows, ActionPublicBaseUrlService publicBaseUrl, ActionRunService actions) {
        this.workflows = workflows;
        this.publicBaseUrl = publicBaseUrl;
        this.actions = actions;
    }
    @GetMapping("/workflows")
    public List<ActionWorkflowDto.Definition> list(@PathVariable String workspaceId, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.list(workspaceId, userId);
    }
    @GetMapping("/workflows/{id}")
    public ActionWorkflowDto.Definition get(@PathVariable String workspaceId, @PathVariable String id,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.get(workspaceId, id, userId);
    }
    @GetMapping("/projects/{projectId}/workflows")
    public List<ActionWorkflowDto.Definition> executable(@PathVariable String workspaceId, @PathVariable String projectId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.listExecutable(workspaceId, projectId, userId);
    }
    @GetMapping("/projects/{projectId}/workflows/{id}/processors")
    public List<ActionDto.ExecutableProcessorResponse> processors(@PathVariable String workspaceId, @PathVariable String projectId,
            @PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.stepProcessors(workspaceId, projectId, id, userId);
    }
    @GetMapping("/projects/{projectId}/workflows/{id}/processors/{definitionId}/parameter-values")
    public ActionDto.ParameterValuesResponse parameterValues(@PathVariable String workspaceId, @PathVariable String projectId,
            @PathVariable String id, @PathVariable String definitionId, @AuthenticationPrincipal(expression = "subject") String userId) {
        return actions.discoverWorkflowParameterValues(workspaceId, projectId, id, definitionId, userId);
    }
    @PostMapping("/workflows")
    public ActionWorkflowDto.Definition create(@PathVariable String workspaceId, @Valid @RequestBody ActionWorkflowDto.SaveRequest request, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.save(workspaceId, null, request, userId);
    }
    @PutMapping("/workflows/{id}")
    public ActionWorkflowDto.Definition update(@PathVariable String workspaceId, @PathVariable String id, @Valid @RequestBody ActionWorkflowDto.SaveRequest request, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.save(workspaceId, id, request, userId);
    }
    @DeleteMapping("/workflows/{id}")
    public ResponseEntity<Void> delete(@PathVariable String workspaceId, @PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        workflows.delete(workspaceId, id, userId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/projects/{projectId}/workflow-runs/impact")
    public ActionDto.RunImpactResponse preview(@PathVariable String workspaceId, @PathVariable String projectId, @Valid @RequestBody ActionWorkflowDto.StartRequest request, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.preview(workspaceId, projectId, request, userId);
    }
    @PostMapping("/projects/{projectId}/workflow-runs")
    public ActionWorkflowDto.Run start(@PathVariable String workspaceId, @PathVariable String projectId, @Valid @RequestBody ActionWorkflowDto.StartRequest request, @AuthenticationPrincipal(expression = "subject") String userId, HttpServletRequest httpRequest) {
        return workflows.start(workspaceId, projectId, request, userId, publicBaseUrl.publicApiBaseUrl(httpRequest));
    }
    @GetMapping("/projects/{projectId}/workflow-runs")
    public List<ActionWorkflowDto.Run> runs(@PathVariable String workspaceId, @PathVariable String projectId, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.listRuns(workspaceId, projectId, userId);
    }
    @GetMapping("/workflow-runs")
    public List<ActionWorkflowDto.Run> workspaceRuns(@PathVariable String workspaceId, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.listWorkspaceRuns(workspaceId, userId);
    }
    @PostMapping("/projects/{projectId}/workflow-runs/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        workflows.dismiss(workspaceId, projectId, id, userId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/projects/{projectId}/workflow-runs/{id}/cancel")
    public ActionWorkflowDto.Run cancel(@PathVariable String workspaceId, @PathVariable String projectId, @PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.cancel(workspaceId, projectId, id, userId);
    }
}
