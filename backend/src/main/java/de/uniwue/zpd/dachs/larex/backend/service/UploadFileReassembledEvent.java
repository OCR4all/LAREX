package de.uniwue.zpd.dachs.larex.backend.service;

public record UploadFileReassembledEvent(
        String sessionId,
        String fileId
) {}
