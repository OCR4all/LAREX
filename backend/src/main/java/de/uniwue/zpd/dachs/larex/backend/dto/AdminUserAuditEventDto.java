package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminUserAuditEventDto(
        String id,
        AdminUserAuditAction action,
        AdminUserAuditOutcome outcome,
        String actorUserId,
        String actorUsername,
        String created,
        String details
) {
}
