package de.uniwue.zpd.dachs.larex.backend.service.annotation.table;

import java.util.List;

public record TableIssue(
        String code,
        Severity severity,
        String tableId,
        List<String> regionIds,
        Integer row,
        Integer col
) {
    public enum Severity { ERROR, WARNING }

    public String describe() {
        String position = row != null && col != null ? " (row " + (row + 1) + ", column " + (col + 1) + ")" : "";
        String scope = tableId != null ? "Table " + tableId + ": " : "";
        return scope + message() + position + ".";
    }

    private String message() {
        return switch (code) {
            case "grid-too-few-rows" -> "The grid needs at least two grid rows";
            case "grid-row-indices" -> "The grid row indices do not count up from 0";
            case "grid-ragged" -> "The grid rows have different numbers of points";
            case "grid-too-few-columns" -> "The grid rows need at least two points";
            case "add-points-invalid" -> "The grid AddPoints cannot be read";
            case "add-points-out-of-range" -> "Grid AddPoints refer to a row or column outside the grid";
            case "dimension-mismatch" -> "The rows and columns attributes do not match the grid or cells";
            case "cell-missing-role" -> "Region " + regionIds.getFirst() + " in the table has no table cell role";
            case "cell-incomplete-role" -> "The table cell role of " + regionIds.getFirst() + " has no row or column index";
            case "cell-invalid-span" -> "Cell " + regionIds.getFirst() + " has a row or column span below 1";
            case "cell-index-out-of-range" -> "Cell " + regionIds.getFirst() + " lies outside the table";
            case "cell-overlap" -> "Cells " + String.join(" and ", regionIds) + " cover the same table position";
            case "uncovered-position" -> "A table position is not covered by any cell";
            case "role-outside-table" -> "Region " + regionIds.getFirst() + " has a table cell role but is not inside a table";
            default -> code;
        };
    }
}
