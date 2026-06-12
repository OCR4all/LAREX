package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import java.util.List;

record ExportTextLine(
        String id,
        String text,
        PolygonDto coords,
        PolygonDto baseline,
        Double confidence,
        Double variantConfidence,
        List<ExportWord> words
) {
    boolean hasText() {
        return text != null && !text.isBlank();
    }
}
