package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TableCellRoleDto(
    Integer rowIndex,
    Integer columnIndex,
    Integer rowSpan,
    Integer colSpan,
    Boolean header
) {}
