package de.uniwue.zpd.dachs.larex.backend.service.export;

import java.util.List;

record ExportTable(
        String id,
        int rows,
        int columns,
        List<Cell> cells
) {
    record Cell(
            String id,
            int row,
            int col,
            int rowSpan,
            int colSpan,
            boolean header,
            String text
    ) {
    }
}
