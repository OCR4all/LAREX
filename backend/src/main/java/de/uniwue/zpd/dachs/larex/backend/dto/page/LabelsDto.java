package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LabelsDto(
    String externalModel,
    String externalId,
    String prefix,
    String comments,
    List<LabelDto> labels
) {}
