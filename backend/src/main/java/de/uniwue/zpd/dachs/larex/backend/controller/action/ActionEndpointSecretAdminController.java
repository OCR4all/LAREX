package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionEndpointSecretService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/actions/endpoint-secrets")
public class ActionEndpointSecretAdminController {

    private final ActionEndpointSecretService secretService;

    public ActionEndpointSecretAdminController(ActionEndpointSecretService secretService) {
        this.secretService = secretService;
    }

    @GetMapping
    public ResponseEntity<List<ActionDto.EndpointSecretResponse>> listSecrets() {
        return ResponseEntity.ok(secretService.listSecrets());
    }

    @PostMapping
    public ResponseEntity<ActionDto.EndpointSecretRevealResponse> createSecret(
            @Valid @RequestBody ActionDto.EndpointSecretRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(secretService.createSecret(request, userId));
    }

    @PostMapping("/{secretId}/rotate")
    public ResponseEntity<ActionDto.EndpointSecretRevealResponse> rotateSecret(@PathVariable String secretId) {
        return ResponseEntity.ok(secretService.rotateSecret(secretId));
    }

    @DeleteMapping("/{secretId}")
    public ResponseEntity<Void> deleteSecret(@PathVariable String secretId) {
        secretService.deleteSecret(secretId);
        return ResponseEntity.noContent().build();
    }
}
