package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.StorageQuotaDto;
import de.uniwue.zpd.dachs.larex.backend.entity.WorkspaceStorageQuota;
import de.uniwue.zpd.dachs.larex.backend.service.WorkspaceStorageQuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/storage/quotas")
public class StorageQuotaController {

    private final WorkspaceStorageQuotaService quotaService;

    public StorageQuotaController(WorkspaceStorageQuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping("/workspace/{workspaceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StorageQuotaDto> getWorkspaceQuota(@PathVariable String workspaceId) {
        WorkspaceStorageQuota quota = quotaService.getOrCreateQuota(workspaceId);
        return ResponseEntity.ok(StorageQuotaDto.fromEntity(quota));
    }

    @GetMapping("/workspace/{workspaceId}/check/{fileSize}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkAvailableSpace(
            @PathVariable String workspaceId,
            @PathVariable Long fileSize) {
        return ResponseEntity.ok(quotaService.hasAvailableSpace(workspaceId, fileSize));
    }

    @PostMapping("/workspace/{workspaceId}/recalculate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StorageQuotaDto> recalculateUsage(@PathVariable String workspaceId) {
        WorkspaceStorageQuota quota = quotaService.recalculateUsage(workspaceId);
        return ResponseEntity.ok(StorageQuotaDto.fromEntity(quota));
    }

    // Admin endpoints

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<List<StorageQuotaDto>> getAllQuotas() {
        List<StorageQuotaDto> quotaDtos = quotaService.getAllQuotas().stream()
                .map(StorageQuotaDto::fromEntity)
                .toList();
        return ResponseEntity.ok(quotaDtos);
    }

    @GetMapping("/admin/exceeded")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<List<StorageQuotaDto>> getExceededQuotas() {
        List<StorageQuotaDto> quotaDtos = quotaService.getExceededQuotas().stream()
                .map(StorageQuotaDto::fromEntity)
                .toList();
        return ResponseEntity.ok(quotaDtos);
    }

    @GetMapping("/admin/above/{percentage}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<List<StorageQuotaDto>> getQuotasAbovePercentage(@PathVariable Double percentage) {
        List<StorageQuotaDto> quotaDtos = quotaService.getQuotasAbovePercentage(percentage).stream()
                .map(StorageQuotaDto::fromEntity)
                .toList();
        return ResponseEntity.ok(quotaDtos);
    }

    @PutMapping("/admin/workspace/{workspaceId}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<StorageQuotaDto> updateQuota(
            @PathVariable String workspaceId,
            @Valid @RequestBody StorageQuotaDto.UpdateQuotaRequest request) {
        WorkspaceStorageQuota quota = quotaService.updateQuotaLimit(
                workspaceId,
                request.quotaLimitBytes(),
                request.isCustom() != null ? request.isCustom() : true
        );
        return ResponseEntity.ok(StorageQuotaDto.fromEntity(quota));
    }

    @GetMapping("/admin/default")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<Long> getDefaultQuota() {
        return ResponseEntity.ok(quotaService.getDefaultQuotaBytes());
    }

    @PostMapping("/admin/reset-defaults")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<Void> resetToDefaults() {
        quotaService.resetToDefaults();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/workspace/{workspaceId}")
    @PreAuthorize("hasRole('GLOBAL_ADMIN')")
    public ResponseEntity<Void> deleteQuota(@PathVariable String workspaceId) {
        quotaService.deleteQuota(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
