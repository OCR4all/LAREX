package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionDefinitionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    public ActionAdminController(ActionDefinitionService definitionService) {
        this.definitionService = definitionService;
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
}
