package de.uniwue.zpd.dachs.larex.backend.service.upload.events;

public record UploadFileReassembledEvent(
        String sessionId,
        String fileId
) {}
