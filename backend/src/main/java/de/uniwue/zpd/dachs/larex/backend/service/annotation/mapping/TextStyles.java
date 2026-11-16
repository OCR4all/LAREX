package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

import de.uniwue.zpd.dachs.larex.backend.dto.page.style.TextStyleDto;

final class TextStyles {

    static final TextStyleDto EMPTY = new TextStyleDto(
        null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null
    );

    private TextStyles() {}

    static TextStyleDto withBackground(String bgColour) {
        return new TextStyleDto(
            null, null, null, null, null, null, null, null, bgColour, null,
            null, null, null, null, null, null, null, null, null, null
        );
    }
}
