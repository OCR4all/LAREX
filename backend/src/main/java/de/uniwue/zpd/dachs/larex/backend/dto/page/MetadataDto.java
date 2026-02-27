package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for PAGE XML metadata, aligned with page4j's MetaData.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetadataDto(
    /** Creator software/tool */
    String creator,
    /** Creation timestamp (ISO 8601) */
    String created,
    /** Last modification timestamp (ISO 8601) */
    String lastChange,
    /** Comments */
    String comments,
    /** External references */
    String externalRef
) {}
