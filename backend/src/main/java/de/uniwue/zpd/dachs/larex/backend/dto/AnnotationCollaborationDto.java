package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public class AnnotationCollaborationDto {

    public record UserSummary(
            String id,
            String username,
            String displayName,
            String avatar
    ) {}

    public record BootstrapResponse(
            String roomKey,
            String workspaceId,
            String projectId,
            String pageId,
            String xmlId,
            String persistedRevision,
            boolean canEdit,
            boolean canForceTakeover,
            UserSummary user,
            LeaseState lease
    ) {}

    public record LeaseOwner(
            UserSummary user,
            String acquiredAt
    ) {}

    public record TakeoverRequest(
            UserSummary requester,
            String requestedAt,
            boolean force
    ) {}

    public record LeaseState(
            LeaseOwner editor,
            TakeoverRequest pendingTakeover,
            boolean leaseOwner,
            long leaseEpoch,
            String expiresAt
    ) {}

    public record LeaseResponse(
            String roomKey,
            LeaseState lease
    ) {}

    public record LeaseInstancePayload(
            String instanceId
    ) {}

    public enum AnnotationScope {
        PROJECT,
        DATASET
    }

    public record LeaseRenewalTarget(
            @NotNull AnnotationScope scope,
            String workspaceId,
            String projectId,
            String pageId,
            String datasetId,
            String itemId,
            @NotBlank String xmlId
    ) {}

    public record LeaseRenewalBatchRequest(
            @NotBlank String instanceId,
            @NotEmpty @Size(max = 100) List<@Valid LeaseRenewalTarget> targets
    ) {}

    public record LeaseRenewalBatchResponse(
            List<LeaseResponse> renewals
    ) {}

    public record TakeoverRequestPayload(
            boolean force
    ) {}

    public record TakeoverResponsePayload(
            String decision,
            String handoffMode
    ) {}

    public record LockErrorResponse(
            int status,
            String error,
            String message,
            String path,
            UserSummary owner,
            String reason
    ) {}

    public record RevisionResponse(
            String workspaceId,
            String projectId,
            String pageId,
            String xmlId,
            String persistedRevision,
            LocalDateTime updated
    ) {}
}
