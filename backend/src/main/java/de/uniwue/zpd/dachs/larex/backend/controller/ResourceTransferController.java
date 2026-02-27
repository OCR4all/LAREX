package de.uniwue.zpd.dachs.larex.backend.controller;

import de.uniwue.zpd.dachs.larex.backend.dto.ResourceTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ResourceTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.service.ResourceTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/resource-transfers")
public class ResourceTransferController {

    private final ResourceTransferService resourceTransferService;

    public ResourceTransferController(ResourceTransferService resourceTransferService) {
        this.resourceTransferService = resourceTransferService;
    }

    @PostMapping
    public ResponseEntity<ResourceTransferDto.Response> requestTransfer(
            @Valid @RequestBody ResourceTransferDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<ResourceTransferRequest> transferOpt = resourceTransferService.requestTransfer(
                request.resourceId(),
                request.resourceType(),
                request.targetWorkspaceId(),
                userId,
                request.message(),
                request.transferType() != null ? request.transferType() : ResourceTransferRequest.TransferType.MOVE
        );

        return transferOpt.map(resourceTransferService::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.badRequest().build());
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<ResourceTransferDto.Response>> getMyRequests(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        List<ResourceTransferDto.Response> response = resourceTransferService.toResponses(
                resourceTransferService.getUserTransferRequests(userId)
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workspace/{workspaceId}/incoming")
    public ResponseEntity<List<ResourceTransferDto.Response>> getIncomingRequestsForWorkspace(
            @PathVariable String workspaceId) {
        return ResponseEntity.ok(resourceTransferService.toResponses(
                resourceTransferService.getPendingIncomingRequestsForWorkspace(workspaceId)
        ));
    }

    @GetMapping("/workspace/{workspaceId}/outgoing")
    public ResponseEntity<List<ResourceTransferDto.Response>> getOutgoingRequestsForWorkspace(
            @PathVariable String workspaceId) {
        return ResponseEntity.ok(resourceTransferService.toResponses(
                resourceTransferService.getPendingOutgoingRequestsForWorkspace(workspaceId)
        ));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<Void> approveRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boolean approved = resourceTransferService.approveTransferRequest(requestId, userId);
        return approved ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable String requestId,
            @Valid @RequestBody ResourceTransferDto.ApprovalRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boolean rejected = resourceTransferService.rejectTransferRequest(requestId, userId, request.rejectionReason());
        return rejected ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        boolean cancelled = resourceTransferService.cancelTransferRequest(requestId, userId);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

}
