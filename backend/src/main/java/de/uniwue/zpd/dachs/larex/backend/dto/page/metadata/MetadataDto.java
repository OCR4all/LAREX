package de.uniwue.zpd.dachs.larex.backend.dto.page.metadata;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.style.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.*;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

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
    String externalRef,
    /** User-defined metadata attributes */
    UserDefinedDto userDefined,
    /** Additional metadata items */
    List<MetadataItemDto> items
) {
    public MetadataDto(
        String creator,
        String created,
        String lastChange,
        String comments,
        String externalRef
    ) {
        this(creator, created, lastChange, comments, externalRef, null, null);
    }
}
