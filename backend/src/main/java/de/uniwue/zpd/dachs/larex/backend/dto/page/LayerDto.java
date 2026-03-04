package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LayerDto(
    String id,
    Integer zIndex,
    String caption,
    List<String> regionRefs
) {}
