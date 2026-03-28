package de.uniwue.zpd.dachs.larex.backend.controller.normalization;

import de.uniwue.zpd.dachs.larex.backend.dto.NormalizationProfileDto;
import de.uniwue.zpd.dachs.larex.backend.service.normalization.NormalizationProfileService;
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
@RequestMapping("/workspaces/{workspaceId}/normalization-profiles")
public class NormalizationProfileController {

    private final NormalizationProfileService normalizationProfileService;

    public NormalizationProfileController(NormalizationProfileService normalizationProfileService) {
        this.normalizationProfileService = normalizationProfileService;
    }

    @GetMapping
    public ResponseEntity<List<NormalizationProfileDto.SummaryResponse>> getProfiles(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(normalizationProfileService.getProfiles(userId, workspaceId));
    }

    @GetMapping("/{profileId}")
    public ResponseEntity<NormalizationProfileDto.Response> getProfile(
            @PathVariable String workspaceId,
            @PathVariable String profileId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(normalizationProfileService.getProfile(userId, workspaceId, profileId));
    }

    @PostMapping
    public ResponseEntity<NormalizationProfileDto.Response> createProfile(
            @PathVariable String workspaceId,
            @Valid @RequestBody NormalizationProfileDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(normalizationProfileService.createProfile(userId, workspaceId, request));
    }

    @PutMapping("/{profileId}")
    public ResponseEntity<NormalizationProfileDto.Response> updateProfile(
            @PathVariable String workspaceId,
            @PathVariable String profileId,
            @Valid @RequestBody NormalizationProfileDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(normalizationProfileService.updateProfile(userId, workspaceId, profileId, request));
    }

    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(
            @PathVariable String workspaceId,
            @PathVariable String profileId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        normalizationProfileService.deleteProfile(userId, workspaceId, profileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{profileId}/normalize-sources")
    public ResponseEntity<NormalizationProfileDto.NormalizeSourcesResponse> normalizeSources(
            @PathVariable String workspaceId,
            @PathVariable String profileId,
            @Valid @RequestBody NormalizationProfileDto.NormalizeSourcesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(normalizationProfileService.normalizeSources(userId, workspaceId, profileId, request));
    }

    @PostMapping("/{profileId}/apply-sources")
    public ResponseEntity<NormalizationProfileDto.ApplySourcesResponse> applySources(
            @PathVariable String workspaceId,
            @PathVariable String profileId,
            @Valid @RequestBody NormalizationProfileDto.NormalizeSourcesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(normalizationProfileService.applySources(userId, workspaceId, profileId, request));
    }
}
