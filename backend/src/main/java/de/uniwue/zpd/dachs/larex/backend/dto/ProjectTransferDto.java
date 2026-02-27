package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ProjectTransferDto {

    public record CreateRequest(
            @NotBlank(message = "Project ID is required")
            String projectId,
            
            @NotBlank(message = "Target workspace ID is required")
            String targetWorkspaceId,
            
            @Size(max = 500, message = "Message cannot exceed 500 characters")
            String message,
            
            ProjectTransferRequest.TransferType transferType
    ) {}

    public record Response(
            String id,
            String projectId,
            String projectName,
            String sourceWorkspaceId,
            String sourceWorkspaceName,
            String targetWorkspaceId,
            String targetWorkspaceName,
            String requestedByUserId,
            String approvedByUserId,
            ProjectTransferRequest.Status status,
            ProjectTransferRequest.TransferType transferType,
            String message,
            String rejectionReason,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record ApprovalRequest(
            @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
            String rejectionReason
    ) {}

    public record TransferSummary(
            String workspaceId,
            String workspaceName,
            long pendingIncoming,
            long pendingOutgoing
    ) {}
}