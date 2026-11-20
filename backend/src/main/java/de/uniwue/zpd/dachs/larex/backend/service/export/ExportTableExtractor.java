package de.uniwue.zpd.dachs.larex.backend.service.export;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.TableCellRoleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ExportTableExtractor {

    private ExportTableExtractor() {
    }

    static List<ExportTable> extractTables(PageDto pageDto, List<ExportRegion> regions) {
        Map<String, ExportRegion> regionById = regions.stream()
                .collect(Collectors.toMap(ExportRegion::id, Function.identity(), (first, second) -> first));
        List<ExportTable> tables = new ArrayList<>();
        collect(pageDto == null ? null : pageDto.regions(), regionById, tables);
        return tables;
    }

    static Comparator<RegionDto> cellOrder() {
        return Comparator.comparing((RegionDto region) -> role(region) == null)
                .thenComparing(region -> role(region) == null ? 0 : Objects.requireNonNullElse(role(region).rowIndex(), 0))
                .thenComparing(region -> role(region) == null ? 0 : Objects.requireNonNullElse(role(region).columnIndex(), 0));
    }

    private static void collect(List<RegionDto> regions, Map<String, ExportRegion> regionById, List<ExportTable> tables) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region == null) {
                continue;
            }
            if (region.kind() == RegionKind.TableRegion) {
                tables.add(toTable(region, regionById));
            }
            collect(region.nestedRegions(), regionById, tables);
        }
    }

    private static ExportTable toTable(RegionDto table, Map<String, ExportRegion> regionById) {
        List<ExportTable.Cell> cells = new ArrayList<>();
        for (RegionDto child : table.nestedRegions() == null ? List.<RegionDto>of() : table.nestedRegions()) {
            TableCellRoleDto role = role(child);
            if (role == null || role.rowIndex() == null || role.columnIndex() == null || role.rowIndex() < 0 || role.columnIndex() < 0) {
                continue;
            }
            ExportRegion exported = regionById.get(child.id());
            cells.add(new ExportTable.Cell(
                    child.id(),
                    role.rowIndex(),
                    role.columnIndex(),
                    Math.max(1, Objects.requireNonNullElse(role.rowSpan(), 1)),
                    Math.max(1, Objects.requireNonNullElse(role.colSpan(), 1)),
                    Boolean.TRUE.equals(role.header()),
                    exported == null || exported.text() == null ? "" : exported.text()
            ));
        }
        cells.sort(Comparator.comparingInt(ExportTable.Cell::row).thenComparingInt(ExportTable.Cell::col));

        int[] size = gridSize(table);
        int rows = size != null ? size[0] : cells.stream().mapToInt(cell -> cell.row() + cell.rowSpan()).max()
                .orElse(Objects.requireNonNullElse(table.rows(), 0));
        int columns = size != null ? size[1] : cells.stream().mapToInt(cell -> cell.col() + cell.colSpan()).max()
                .orElse(Objects.requireNonNullElse(table.columns(), 0));
        List<ExportTable.Cell> inside = cells.stream()
                .filter(cell -> cell.row() < rows && cell.col() < columns)
                .map(cell -> new ExportTable.Cell(cell.id(), cell.row(), cell.col(),
                        Math.min(cell.rowSpan(), rows - cell.row()),
                        Math.min(cell.colSpan(), columns - cell.col()),
                        cell.header(), cell.text()))
                .toList();
        return new ExportTable(table.id(), rows, columns, inside);
    }

    private static int[] gridSize(RegionDto table) {
        List<GridPointsDto> gridRows = table.grid() == null ? null : table.grid().rows();
        if (gridRows == null || gridRows.size() < 2) {
            return null;
        }
        int width = gridRows.getFirst().points() == null ? 0 : gridRows.getFirst().points().points().size();
        boolean consistent = width >= 2 && gridRows.stream()
                .allMatch(row -> row.points() != null && row.points().points().size() == width);
        return consistent ? new int[]{gridRows.size() - 1, width - 1} : null;
    }

    private static TableCellRoleDto role(RegionDto region) {
        return region.roles() == null ? null : region.roles().tableCellRole();
    }
}
