package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TextStyleDto(
    String fontFamily,
    Boolean serif,
    Boolean monospace,
    Double fontSize,
    Integer xHeight,
    Integer kerning,
    String textColour,
    Integer textColourRgb,
    String bgColour,
    Integer bgColourRgb,
    Boolean reverseVideo,
    Boolean bold,
    Boolean italic,
    Boolean underlined,
    String underlineStyle,
    Boolean subscript,
    Boolean superscript,
    Boolean strikethrough,
    Boolean smallCaps,
    Boolean letterSpaced
) {}
