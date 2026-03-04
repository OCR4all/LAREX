package de.uniwue.zpd.dachs.larex.backend.service.annotation;

import de.uniwue.zpd.dachs.larex.backend.dto.page.PageDto;

public record AnnotationSavedEvent(
        String xmlId,
        String pageId,
        String projectId,
        PageDto pageDto
) {}
