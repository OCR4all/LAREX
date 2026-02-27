package de.uniwue.zpd.dachs.larex.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import de.uniwue.zpd.dachs.larex.backend.service.LabelSetService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/label-sets")
public class LabelSetController {

    private final LabelSetService labelSetService;

    public LabelSetController(LabelSetService labelSetService) {
        this.labelSetService = labelSetService;
    }

    @GetMapping
    public ResponseEntity<List<LabelSetDto.SummaryResponse>> getLabelSets(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<LabelSetDto.SummaryResponse> labelSets;

        if (search != null && !search.trim().isEmpty()) {
            labelSets = labelSetService.searchLabelSets(userId, workspaceId, search);
        } else {
            labelSets = labelSetService.getLabelSets(userId, workspaceId);
        }

        return ResponseEntity.ok(labelSets);
    }

    @GetMapping("/{labelSetId}")
    public ResponseEntity<LabelSetDto.Response> getLabelSet(
            @PathVariable String workspaceId,
            @PathVariable String labelSetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        LabelSetDto.Response labelSet = labelSetService.getLabelSet(userId, workspaceId, labelSetId);
        return ResponseEntity.ok(labelSet);
    }

    @PostMapping
    public ResponseEntity<LabelSetDto.Response> createLabelSet(
            @PathVariable String workspaceId,
            @RequestBody JsonNode request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        LabelSetDto.Response labelSet = labelSetService.createLabelSet(userId, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(labelSet);
    }

    @PutMapping("/{labelSetId}")
    public ResponseEntity<LabelSetDto.Response> updateLabelSet(
            @PathVariable String workspaceId,
            @PathVariable String labelSetId,
            @RequestBody JsonNode request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        LabelSetDto.Response labelSet = labelSetService.updateLabelSet(userId, workspaceId, labelSetId, request);
        return ResponseEntity.ok(labelSet);
    }

    @DeleteMapping("/{labelSetId}")
    public ResponseEntity<Void> deleteLabelSet(
            @PathVariable String workspaceId,
            @PathVariable String labelSetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        labelSetService.deleteLabelSet(userId, workspaceId, labelSetId);
        return ResponseEntity.noContent().build();
    }
}