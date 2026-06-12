package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;

record ExportWord(
        String id,
        String text,
        PolygonDto coords,
        Double confidence,
        Double variantConfidence
) {
    boolean hasText() {
        return text != null && !text.isBlank();
    }
}
