package de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;

import java.util.Set;

public record PageXmlParseResult(PageDto pageDto, Set<PageXmlAttributeScanner.AttributeOccurrence> attributeOccurrences) {}
