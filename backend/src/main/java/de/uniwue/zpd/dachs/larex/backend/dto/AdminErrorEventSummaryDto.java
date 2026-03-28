package de.uniwue.zpd.dachs.larex.backend.dto;

public record AdminErrorEventSummaryDto(
        String id,
        String created,
        int status,
        String severity,
        String code,
        String error,
        String message,
        String path,
        String method,
        String userId,
        String username,
        String workspaceId
) {
}
