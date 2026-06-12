package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import java.util.List;

record ExportRegion(
        String id,
        String parentRegionId,
        RegionKind kind,
        String type,
        Integer readingOrderIndex,
        PolygonDto coords,
        Integer rows,
        Integer columns,
        List<String> labelIds,
        String custom,
        String text,
        List<ExportTextLine> lines
) {
    boolean hasText() {
        return hasText(text) || lines.stream().anyMatch(ExportTextLine::hasText);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
