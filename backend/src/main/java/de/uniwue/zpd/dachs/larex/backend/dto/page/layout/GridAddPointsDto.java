package de.uniwue.zpd.dachs.larex.backend.dto.page.layout;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GridAddPointsDto(
    Integer row,
    Integer col,
    String left,
    String top
) {}
