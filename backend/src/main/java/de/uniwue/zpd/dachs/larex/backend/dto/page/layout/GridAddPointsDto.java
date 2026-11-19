package de.uniwue.zpd.dachs.larex.backend.dto.page.layout;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GridAddPointsDto(
    Integer row,
    Integer col,
    String left,
    String top,
    PolygonDto leftPoints,
    PolygonDto topPoints
) {}
