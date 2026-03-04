package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GraphemeElementDto(
    String kind,
    String id,
    Integer index,
    String charType,
    Boolean ligature,
    String custom,
    String comments,
    PolygonDto coords,
    List<TextContentVariantDto> textContentVariants,
    List<LabelsDto> labels,
    List<GraphemeElementDto> members
) {}
