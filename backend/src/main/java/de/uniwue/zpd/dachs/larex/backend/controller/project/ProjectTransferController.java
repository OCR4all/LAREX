package de.uniwue.zpd.dachs.larex.backend.controller.project;

import de.uniwue.zpd.dachs.larex.backend.dto.ProjectTransferDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.service.project.ProjectTransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/project-transfers")
public class ProjectTransferController {

    private final ProjectTransferService projectTransferService;

    public ProjectTransferController(ProjectTransferService projectTransferService) {
        this.projectTransferService = projectTransferService;
    }

    @PostMapping
    public ResponseEntity<ProjectTransferDto.Response> requestTransfer(
            @Valid @RequestBody ProjectTransferDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Optional<ProjectTransferRequest> transferOpt = projectTransferService.requestProjectTransfer(
                request.projectId(),
                request.targetWorkspaceId(),
                userId,
                request.message(),
                request.transferType() != null ? request.transferType() : ProjectTransferRequest.TransferType.MOVE,
                request.projectName()
        );

        return transferOpt.map(projectTransferService::toResponse)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response))
                .orElse(ResponseEntity.badRequest().build());
    }

    @PostMapping("/bulk")
    public ResponseEntity<ProjectTransferDto.BatchCreateResponse> requestTransfers(
            @Valid @RequestBody ProjectTransferDto.BatchCreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<String> projectIds = request.projectIds().stream().distinct().toList();
        List<ProjectTransferRequest> transfers = projectTransferService.requestProjectTransfers(
                projectIds,
                request.targetWorkspaceId(),
                userId,
                request.message(),
                request.transferType() != null ? request.transferType() : ProjectTransferRequest.TransferType.MOVE
        );
        List<String> succeededIds = transfers.stream().map(ProjectTransferRequest::getProjectId).toList();
        List<String> failedIds = projectIds.stream().filter(id -> !succeededIds.contains(id)).toList();
        List<ProjectTransferDto.Response> responses = projectTransferService.toResponses(transfers);

        return ResponseEntity.status(HttpStatus.CREATED).body(new ProjectTransferDto.BatchCreateResponse(
                responses,
                failedIds,
                responses.size(),
                failedIds.size()
        ));
    }

    @GetMapping("/name-availability")
    public ResponseEntity<ProjectTransferDto.NameAvailabilityResponse> checkProjectNameAvailability(
            @RequestParam String projectId,
            @RequestParam String targetWorkspaceId,
            @RequestParam String projectName,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean available = projectTransferService.isProjectNameAvailable(
                projectId, targetWorkspaceId, projectName, userId
        );
        return ResponseEntity.ok(new ProjectTransferDto.NameAvailabilityResponse(available));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<ProjectTransferDto.Response>> getMyRequests(
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<ProjectTransferDto.Response> response = projectTransferService.toResponses(
                projectTransferService.getUserTransferRequests(userId)
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/workspace/{workspaceId}/incoming")
    public ResponseEntity<List<ProjectTransferDto.Response>> getIncomingRequestsForWorkspace(
            @PathVariable String workspaceId) {
        return ResponseEntity.ok(projectTransferService.toResponses(
                projectTransferService.getPendingIncomingRequestsForWorkspace(workspaceId)
        ));
    }

    @GetMapping("/workspace/{workspaceId}/outgoing")
    public ResponseEntity<List<ProjectTransferDto.Response>> getOutgoingRequestsForWorkspace(
            @PathVariable String workspaceId) {
        return ResponseEntity.ok(projectTransferService.toResponses(
                projectTransferService.getPendingOutgoingRequestsForWorkspace(workspaceId)
        ));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<Void> approveRequest(
            @PathVariable String requestId,
            @Valid @RequestBody(required = false) ProjectTransferDto.ApproveRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean approved = projectTransferService.approveTransferRequest(
                requestId, userId, request != null ? request.projectName() : null
        );
        return approved ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<Void> rejectRequest(
            @PathVariable String requestId,
            @Valid @RequestBody ProjectTransferDto.ApprovalRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean rejected = projectTransferService.rejectTransferRequest(
                requestId, userId, request.rejectionReason()
        );
        return rejected ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping("/{requestId}/cancel")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        boolean cancelled = projectTransferService.cancelTransferRequest(requestId, userId);
        return cancelled ? ResponseEntity.ok().build() : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

}
