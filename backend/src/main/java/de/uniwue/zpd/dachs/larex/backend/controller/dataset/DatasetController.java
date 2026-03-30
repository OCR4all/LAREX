package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public ResponseEntity<List<DatasetDto.SummaryResponse>> listDatasets(
            @PathVariable String workspaceId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.listDatasets(workspaceId, userId));
    }

    @PostMapping
    public ResponseEntity<DatasetDto.DetailResponse> createDataset(
            @PathVariable String workspaceId,
            @Valid @RequestBody DatasetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(datasetService.createDataset(workspaceId, request, userId));
    }

    @GetMapping("/{datasetId}")
    public ResponseEntity<DatasetDto.DetailResponse> getDataset(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.getDataset(workspaceId, datasetId, userId));
    }

    @PutMapping("/{datasetId}")
    public ResponseEntity<DatasetDto.DetailResponse> updateDataset(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @Valid @RequestBody DatasetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.updateDataset(workspaceId, datasetId, request, userId));
    }

    @DeleteMapping("/{datasetId}")
    public ResponseEntity<Void> deleteDataset(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        datasetService.deleteDataset(workspaceId, datasetId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{datasetId}/items")
    public ResponseEntity<DatasetDto.DetailResponse> addItems(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @Valid @RequestBody DatasetDto.AddItemsRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        return ResponseEntity.ok(datasetService.addItems(workspaceId, datasetId, request, userId));
    }

    @PatchMapping("/{datasetId}/items/{itemId}")
    public ResponseEntity<DatasetDto.DetailResponse> updateItem(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @RequestBody DatasetDto.UpdateItemRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.updateItem(workspaceId, datasetId, itemId, request, userId));
    }

    @DeleteMapping("/{datasetId}/items/{itemId}")
    public ResponseEntity<DatasetDto.DetailResponse> deleteItem(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @PathVariable String itemId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.deleteItem(workspaceId, datasetId, itemId, userId));
    }

    @PostMapping("/{datasetId}/split-generate")
    public ResponseEntity<DatasetDto.DetailResponse> generateSplit(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @RequestBody(required = false) DatasetDto.GenerateSplitRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.generateSplit(workspaceId, datasetId, request, userId));
    }

    @PostMapping("/{datasetId}/validate")
    public ResponseEntity<DatasetDto.ValidationResponse> validateDataset(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(datasetService.validateDataset(workspaceId, datasetId, userId));
    }

    @PostMapping("/{datasetId}/export-package")
    public ResponseEntity<byte[]> exportPackage(
            @PathVariable String workspaceId,
            @PathVariable String datasetId,
            @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        byte[] packageBytes = datasetService.exportDatasetPackage(workspaceId, datasetId, userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("dataset-" + datasetId + ".zip")
                .build());
        return ResponseEntity.ok().headers(headers).body(packageBytes);
    }
}
