package de.uniwue.zpd.dachs.larex.backend.service;

import java.util.Set;

public record UploadPageIndexingRequestedEvent(
        String sessionId,
        String projectId,
        Set<String> pageIds
) {}
