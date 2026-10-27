package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionWorkflowDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/admin/actions/workflows")
public class ActionWorkflowAdminController {
    private final ActionWorkflowService workflows;
    public ActionWorkflowAdminController(ActionWorkflowService workflows) { this.workflows = workflows; }

    @GetMapping
    public List<ActionWorkflowDto.Definition> list(@AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.list(null, userId);
    }
    @GetMapping("/{id}")
    public ActionWorkflowDto.Definition get(@PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.get(null, id, userId);
    }
    @GetMapping("/{id}/runs")
    public List<ActionWorkflowDto.Run> runs(@PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.listAdminRuns(id, userId);
    }
    @PostMapping("/{id}/runs/{runId}/cancel")
    public ActionWorkflowDto.Run cancel(@PathVariable String id, @PathVariable String runId,
                                         @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.cancelAdminRun(id, runId, userId);
    }
    @PostMapping
    public ActionWorkflowDto.Definition create(@Valid @RequestBody ActionWorkflowDto.SaveRequest request,
                                               @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.save(null, null, request, userId);
    }
    @PutMapping("/{id}")
    public ActionWorkflowDto.Definition update(@PathVariable String id, @Valid @RequestBody ActionWorkflowDto.SaveRequest request,
                                               @AuthenticationPrincipal(expression = "subject") String userId) {
        return workflows.save(null, id, request, userId);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @AuthenticationPrincipal(expression = "subject") String userId) {
        workflows.delete(null, id, userId);
        return ResponseEntity.noContent().build();
    }
}
