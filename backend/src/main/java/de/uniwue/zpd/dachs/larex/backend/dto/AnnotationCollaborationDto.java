package de.uniwue.zpd.dachs.larex.backend.dto;

import java.time.LocalDateTime;

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
            long leaseEpoch
    ) {}

    public record LeaseResponse(
            String roomKey,
            LeaseState lease
    ) {}

    public record LeaseInstancePayload(
            String instanceId
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
