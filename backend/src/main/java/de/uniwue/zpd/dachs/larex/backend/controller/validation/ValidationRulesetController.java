package de.uniwue.zpd.dachs.larex.backend.controller.validation;

import de.uniwue.zpd.dachs.larex.backend.dto.ValidationRulesetDto;
import de.uniwue.zpd.dachs.larex.backend.service.validation.ValidationRulesetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/validation-rulesets")
public class ValidationRulesetController {

    private final ValidationRulesetService validationRulesetService;

    public ValidationRulesetController(ValidationRulesetService validationRulesetService) {
        this.validationRulesetService = validationRulesetService;
    }

    @GetMapping
    public ResponseEntity<List<ValidationRulesetDto.SummaryResponse>> getRulesets(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(validationRulesetService.getRulesets(userId, workspaceId));
    }

    @GetMapping("/{rulesetId}")
    public ResponseEntity<ValidationRulesetDto.Response> getRuleset(
            @PathVariable String workspaceId,
            @PathVariable String rulesetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(validationRulesetService.getRuleset(userId, workspaceId, rulesetId));
    }

    @PostMapping
    public ResponseEntity<ValidationRulesetDto.Response> createRuleset(
            @PathVariable String workspaceId,
            @Valid @RequestBody ValidationRulesetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(validationRulesetService.createRuleset(userId, workspaceId, request));
    }

    @PutMapping("/{rulesetId}")
    public ResponseEntity<ValidationRulesetDto.Response> updateRuleset(
            @PathVariable String workspaceId,
            @PathVariable String rulesetId,
            @Valid @RequestBody ValidationRulesetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(validationRulesetService.updateRuleset(userId, workspaceId, rulesetId, request));
    }

    @DeleteMapping("/{rulesetId}")
    public ResponseEntity<Void> deleteRuleset(
            @PathVariable String workspaceId,
            @PathVariable String rulesetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        validationRulesetService.deleteRuleset(userId, workspaceId, rulesetId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{rulesetId}/validate-against-sources")
    public ResponseEntity<ValidationRulesetDto.ValidateAgainstSourcesResponse> validateAgainstSources(
            @PathVariable String workspaceId,
            @PathVariable String rulesetId,
            @Valid @RequestBody ValidationRulesetDto.ValidateAgainstSourcesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(validationRulesetService.validateAgainstSources(userId, workspaceId, rulesetId, request));
    }
}
