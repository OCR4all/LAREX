package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;

/** PAGE annotation DTO together with exact source-presence metadata. */
public record PageXmlParseResult(PageDto pageDto, PageXmlPresenceIndex presenceIndex) {}
