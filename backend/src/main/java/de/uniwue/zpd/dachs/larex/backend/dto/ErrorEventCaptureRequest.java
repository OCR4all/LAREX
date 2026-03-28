package de.uniwue.zpd.dachs.larex.backend.dto;

public record ErrorEventCaptureRequest(
        int status,
        String code,
        String error,
        String message,
        String path,
        String method,
        String exceptionClass,
        String userId,
        String username,
        String workspaceId,
        String detailsJson,
        String stackTrace
) {
}
