package de.uniwue.zpd.dachs.larex.backend.service.annotation.events;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;

public record AnnotationSavedEvent(
        String xmlId,
        String pageId,
        String projectId,
        PageDto pageDto
) {}
