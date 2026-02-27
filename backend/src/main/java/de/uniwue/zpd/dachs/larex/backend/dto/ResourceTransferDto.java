package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.ResourceTransferRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ResourceTransferDto {

    public record CreateRequest(
            @NotBlank(message = "Resource ID is required")
            String resourceId,
            
            @NotNull(message = "Resource type is required")
            ResourceTransferRequest.ResourceType resourceType,
            
            @NotBlank(message = "Target workspace ID is required")
            String targetWorkspaceId,
            
            @Size(max = 500, message = "Message cannot exceed 500 characters")
            String message,
            
            ResourceTransferRequest.TransferType transferType
    ) {}

    public record Response(
            String id,
            String resourceId,
            String resourceName,
            ResourceTransferRequest.ResourceType resourceType,
            String sourceWorkspaceId,
            String sourceWorkspaceName,
            String targetWorkspaceId,
            String targetWorkspaceName,
            String requestedByUserId,
            String approvedByUserId,
            ResourceTransferRequest.Status status,
            ResourceTransferRequest.TransferType transferType,
            String message,
            String rejectionReason,
            LocalDateTime created,
            LocalDateTime updated
    ) {}

    public record ApprovalRequest(
            @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
            String rejectionReason
    ) {}
}
