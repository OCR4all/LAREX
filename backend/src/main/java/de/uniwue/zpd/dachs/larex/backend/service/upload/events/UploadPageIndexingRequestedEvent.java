package de.uniwue.zpd.dachs.larex.backend.service.upload.events;

import java.util.Set;

public record UploadPageIndexingRequestedEvent(
        String sessionId,
        String projectId,
        Set<String> pageIds
) {}
