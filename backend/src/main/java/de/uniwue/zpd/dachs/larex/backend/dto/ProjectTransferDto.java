package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.ProjectTransferRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class ProjectTransferDto {

    public record CreateRequest(
            @NotBlank(message = "Project ID is required")
            String projectId,
            
            @NotBlank(message = "Target workspace ID is required")
            String targetWorkspaceId,
            
            @Size(max = 500, message = "Message cannot exceed 500 characters")
            String message,

            ProjectTransferRequest.TransferType transferType,

            @Size(max = 255, message = "Project name cannot exceed 255 characters")
            String projectName
    ) {
        public CreateRequest(String projectId, String targetWorkspaceId, String message,
                             ProjectTransferRequest.TransferType transferType) {
            this(projectId, targetWorkspaceId, message, transferType, null);
        }
    }

    public record BatchCreateRequest(
            @NotEmpty(message = "At least one project is required")
            @Size(max = 100, message = "Cannot share more than 100 projects at once")
            List<@NotBlank(message = "Project ID must not be blank") String> projectIds,

            @NotBlank(message = "Target workspace ID is required")
            String targetWorkspaceId,

            @Size(max = 500, message = "Message cannot exceed 500 characters")
            String message,

            ProjectTransferRequest.TransferType transferType
    ) {}

    public record BatchCreateResponse(
            List<Response> transfers,
            List<String> failedProjectIds,
            int successCount,
            int failedCount
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

    public record ApproveRequest(
            @Size(max = 255, message = "Project name cannot exceed 255 characters")
            String projectName
    ) {}

    public record NameAvailabilityResponse(
            boolean available
    ) {}

    public record TransferSummary(
            String workspaceId,
            String workspaceName,
            long pendingIncoming,
            long pendingOutgoing
    ) {}
}
