package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RelationDto(
    String id,
    String type,
    String sourceRegionRef,
    String targetRegionRef,
    String custom,
    String comments,
    List<LabelsDto> labels
) {}
