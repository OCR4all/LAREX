package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.ManagedDatasetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workspaces/{workspaceId}/project-datasets")
public class ManagedDatasetController {

    private final ManagedDatasetService managedDatasetService;

    public ManagedDatasetController(ManagedDatasetService managedDatasetService) {
        this.managedDatasetService = managedDatasetService;
    }

    @PostMapping
    public ResponseEntity<DatasetDto.ProjectDatasetResponse> syncProjectDataset(
            @PathVariable String workspaceId,
            @Valid @RequestBody DatasetDto.ProjectDatasetRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(managedDatasetService.syncProjectDataset(workspaceId, request.projectIds(), userId));
    }

    @PostMapping("/{datasetId}/save")
    public ResponseEntity<DatasetDto.DetailResponse> saveAsDataset(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @Valid @RequestBody DatasetDto.SaveProjectDatasetRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(managedDatasetService.saveAsDataset(workspaceId, datasetId, request, userId));
    }
}
