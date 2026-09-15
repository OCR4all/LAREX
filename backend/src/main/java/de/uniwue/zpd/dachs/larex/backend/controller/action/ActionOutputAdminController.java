package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.StorageCleanupDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionOutputDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionOutputService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/admin/outputs")
@Validated
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class ActionOutputAdminController {
    private final ActionOutputService outputService;

    public ActionOutputAdminController(ActionOutputService outputService) {
        this.outputService = outputService;
    }

    @GetMapping
    public ActionOutputDto.AdminOutputPageResponse list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "created") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) @Size(max = 200) String search) {
        if (search == null || search.isBlank()) {
            return outputService.listAdminOutputs(page, size, sort, direction);
        }
        return outputService.listAdminOutputs(page, size, sort, direction, search);
    }

    @GetMapping("/cleanup-preview")
    public List<ActionOutputDto.AdminOutputResponse> cleanupPreview(
            @RequestParam @Min(1) int olderThanDays) {
        return outputService.listAdminOutputsOlderThan(olderThanDays);
    }

    @DeleteMapping("/{outputId}")
    public ResponseEntity<StorageCleanupDto.CleanupResponse> delete(@PathVariable String outputId) {
        return ResponseEntity.ok(outputService.deleteAdminOutput(outputId));
    }

    @PostMapping("/cleanup")
    public StorageCleanupDto.CleanupResponse deleteOlderThan(
            @Valid @RequestBody ActionOutputDto.AdminCleanupRequest request) {
        return outputService.deleteAdminOutputsOlderThan(request.olderThanDays(), request.outputIds());
    }
}
