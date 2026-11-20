package de.uniwue.zpd.dachs.larex.backend.service.annotation.table;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridAddPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.GridPointsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.TableCellRoleDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class TableValidation {

    private record Cell(String regionId, int row, int col, int rowSpan, int colSpan) {}

    private TableValidation() {}

    public static List<TableIssue> validate(PageDto page) {
        List<TableIssue> issues = new ArrayList<>();
        if (page != null) {
            validateRegions(page.regions(), null, issues);
        }
        return issues;
    }

    private static void validateRegions(List<RegionDto> regions, RegionDto parent, List<TableIssue> issues) {
        if (regions == null) {
            return;
        }
        boolean insideTable = parent != null && parent.kind() == RegionKind.TableRegion;
        for (RegionDto region : regions) {
            if (region.roles() != null && region.roles().tableCellRole() != null && !insideTable) {
                issues.add(issue("role-outside-table", TableIssue.Severity.WARNING, null, List.of(region.id()), null, null));
            }
            if (region.kind() == RegionKind.TableRegion) {
                validateTable(region, issues);
            }
            validateRegions(region.nestedRegions(), region, issues);
        }
    }

    static void validateTable(RegionDto table, List<TableIssue> issues) {
        String tableId = table.id();
        List<GridPointsDto> gridRows = table.grid() != null && table.grid().rows() != null ? table.grid().rows() : List.of();
        boolean gridValid = validateGrid(tableId, gridRows, issues);
        int[] gridSize = gridValid && !gridRows.isEmpty()
                ? new int[]{gridRows.size() - 1, gridRows.getFirst().points().points().size() - 1}
                : null;

        if (gridSize != null && !addPointsReadable(table.grid().addPoints())) {
            issues.add(issue("add-points-invalid", TableIssue.Severity.ERROR, tableId, List.of(tableId), null, null));
        }

        List<Cell> cells = new ArrayList<>();
        for (RegionDto child : table.nestedRegions() == null ? List.<RegionDto>of() : table.nestedRegions()) {
            TableCellRoleDto role = child.roles() != null ? child.roles().tableCellRole() : null;
            if (role == null) {
                issues.add(issue("cell-missing-role", TableIssue.Severity.WARNING, tableId, List.of(child.id()), null, null));
            } else if (role.rowIndex() == null || role.columnIndex() == null) {
                issues.add(issue("cell-incomplete-role", TableIssue.Severity.ERROR, tableId, List.of(child.id()), null, null));
            } else {
                cells.add(new Cell(
                        child.id(),
                        role.rowIndex(),
                        role.columnIndex(),
                        Objects.requireNonNullElse(role.rowSpan(), 1),
                        Objects.requireNonNullElse(role.colSpan(), 1)));
            }
        }

        int rows;
        int columns;
        if (gridSize != null) {
            rows = gridSize[0];
            columns = gridSize[1];
        } else if (!cells.isEmpty()) {
            rows = cells.stream().mapToInt(cell -> cell.row() + Math.max(cell.rowSpan(), 1)).max().orElse(0);
            columns = cells.stream().mapToInt(cell -> cell.col() + Math.max(cell.colSpan(), 1)).max().orElse(0);
        } else {
            rows = Objects.requireNonNullElse(table.rows(), 0);
            columns = Objects.requireNonNullElse(table.columns(), 0);
        }

        if (gridSize != null && table.grid().addPoints() != null) {
            for (GridAddPointsDto entry : table.grid().addPoints()) {
                if (entry.row() == null || entry.col() == null) {
                    continue;
                }
                if (entry.row() < 0 || entry.col() < 0 || entry.row() > rows || entry.col() > columns) {
                    issues.add(issue("add-points-out-of-range", TableIssue.Severity.ERROR, tableId, List.of(tableId), entry.row(), entry.col()));
                }
            }
        }

        if ((table.rows() != null && table.rows() != rows) || (table.columns() != null && table.columns() != columns)) {
            issues.add(issue("dimension-mismatch", TableIssue.Severity.WARNING, tableId, List.of(tableId), null, null));
        }

        Map<String, String> occupant = new HashMap<>();
        Set<String> reportedOverlaps = new HashSet<>();
        for (Cell cell : cells) {
            if (cell.rowSpan() < 1 || cell.colSpan() < 1) {
                issues.add(issue("cell-invalid-span", TableIssue.Severity.ERROR, tableId, List.of(cell.regionId()), cell.row(), cell.col()));
                continue;
            }
            if (cell.row() < 0 || cell.col() < 0 || cell.row() + cell.rowSpan() > rows || cell.col() + cell.colSpan() > columns) {
                issues.add(issue("cell-index-out-of-range", TableIssue.Severity.ERROR, tableId, List.of(cell.regionId()), cell.row(), cell.col()));
                continue;
            }
            for (int r = cell.row(); r < cell.row() + cell.rowSpan(); r++) {
                for (int c = cell.col(); c < cell.col() + cell.colSpan(); c++) {
                    String key = r + ":" + c;
                    String other = occupant.putIfAbsent(key, cell.regionId());
                    if (other == null) {
                        continue;
                    }
                    String pairKey = other.compareTo(cell.regionId()) < 0 ? other + "|" + cell.regionId() : cell.regionId() + "|" + other;
                    if (reportedOverlaps.add(pairKey)) {
                        issues.add(issue("cell-overlap", TableIssue.Severity.ERROR, tableId, List.of(other, cell.regionId()), r, c));
                    }
                }
            }
        }

        if (!cells.isEmpty()) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    if (!occupant.containsKey(r + ":" + c)) {
                        issues.add(issue("uncovered-position", TableIssue.Severity.WARNING, tableId, List.of(tableId), r, c));
                    }
                }
            }
        }
    }

    private static boolean validateGrid(String tableId, List<GridPointsDto> gridRows, List<TableIssue> issues) {
        if (gridRows.isEmpty()) {
            return true;
        }
        boolean valid = true;
        if (gridRows.size() < 2) {
            issues.add(issue("grid-too-few-rows", TableIssue.Severity.ERROR, tableId, List.of(tableId), null, null));
            valid = false;
        }
        if (gridRows.stream().anyMatch(row -> row.index() != null)) {
            List<Integer> sorted = gridRows.stream()
                    .map(row -> Objects.requireNonNullElse(row.index(), -1))
                    .sorted(Comparator.naturalOrder())
                    .toList();
            for (int position = 0; position < sorted.size(); position++) {
                if (sorted.get(position) != position) {
                    issues.add(issue("grid-row-indices", TableIssue.Severity.ERROR, tableId, List.of(tableId), null, null));
                    valid = false;
                    break;
                }
            }
        }
        Set<Integer> widths = new HashSet<>();
        for (GridPointsDto row : gridRows) {
            widths.add(pointCount(row.points()));
        }
        if (widths.size() > 1) {
            issues.add(issue("grid-ragged", TableIssue.Severity.ERROR, tableId, List.of(tableId), null, null));
            valid = false;
        } else if (widths.iterator().next() < 2) {
            issues.add(issue("grid-too-few-columns", TableIssue.Severity.ERROR, tableId, List.of(tableId), null, null));
            valid = false;
        }
        return valid;
    }

    private static boolean addPointsReadable(List<GridAddPointsDto> addPoints) {
        if (addPoints == null) {
            return true;
        }
        for (GridAddPointsDto entry : addPoints) {
            if (entry.row() == null || entry.col() == null) {
                return false;
            }
            if (!sideReadable(entry.leftPoints(), entry.left()) || !sideReadable(entry.topPoints(), entry.top())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sideReadable(PolygonDto points, String text) {
        return points != null || text == null || text.isBlank();
    }

    private static int pointCount(PolygonDto polygon) {
        return polygon == null ? 0 : polygon.points().size();
    }

    private static TableIssue issue(String code, TableIssue.Severity severity, String tableId, List<String> regionIds, Integer row, Integer col) {
        return new TableIssue(code, severity, tableId, regionIds, row, col);
    }
}
