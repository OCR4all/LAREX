import type { Region } from '@/models/editor/region'
import { buildTableModel, isTableRegion } from './table-model'

export type TableIssueCode
  = | 'grid-too-few-rows'
    | 'grid-row-indices'
    | 'grid-ragged'
    | 'grid-too-few-columns'
    | 'add-points-invalid'
    | 'add-points-out-of-range'
    | 'dimension-mismatch'
    | 'cell-missing-role'
    | 'cell-incomplete-role'
    | 'cell-invalid-span'
    | 'cell-index-out-of-range'
    | 'cell-overlap'
    | 'uncovered-position'
    | 'role-outside-table'

export type TableIssueSeverity = 'error' | 'warning'

export interface TableIssue {
  code: TableIssueCode
  severity: TableIssueSeverity
  tableId?: string
  regionIds: string[]
  row?: number
  col?: number
}

function validateGrid(table: Region, issues: TableIssue[]): boolean {
  const rows = table.grid?.rows
  if (!rows || rows.length === 0) return true
  const issue = (code: TableIssueCode) => issues.push({ code, severity: 'error', tableId: table.id, regionIds: [table.id] })

  let valid = true
  if (rows.length < 2) {
    issue('grid-too-few-rows')
    valid = false
  }
  const indices = rows.map(row => row.index)
  if (indices.some(index => index !== undefined)) {
    const sorted = [...indices].sort((a, b) => (a ?? -1) - (b ?? -1))
    if (sorted.some((index, position) => index !== position)) {
      issue('grid-row-indices')
      valid = false
    }
  }
  const widths = new Set(rows.map(row => row.points.points.length))
  if (widths.size > 1) {
    issue('grid-ragged')
    valid = false
  } else if ([...widths][0]! < 2) {
    issue('grid-too-few-columns')
    valid = false
  }
  return valid
}

export function validateTable(table: Region): TableIssue[] {
  const issues: TableIssue[] = []
  const gridValid = validateGrid(table, issues)
  const model = buildTableModel(table)
  const tableId = table.id

  if (gridValid && table.grid?.rows?.length && !model.grid) {
    issues.push({ code: 'add-points-invalid', severity: 'error', tableId, regionIds: [tableId] })
  }

  for (const entry of model.grid?.addPoints ?? []) {
    if (entry.row < 0 || entry.col < 0 || entry.row > model.rows || entry.col > model.columns) {
      issues.push({ code: 'add-points-out-of-range', severity: 'error', tableId, regionIds: [tableId], row: entry.row, col: entry.col })
    }
  }

  const declaredRows = table.rows
  const declaredColumns = table.columns
  if ((declaredRows !== undefined && declaredRows !== model.rows) || (declaredColumns !== undefined && declaredColumns !== model.columns)) {
    issues.push({ code: 'dimension-mismatch', severity: 'warning', tableId, regionIds: [tableId] })
  }

  for (const child of table.regions ?? []) {
    const role = child.roles?.tableCellRole
    if (!role) {
      issues.push({ code: 'cell-missing-role', severity: 'warning', tableId, regionIds: [child.id] })
    } else if (role.rowIndex === undefined || role.columnIndex === undefined) {
      issues.push({ code: 'cell-incomplete-role', severity: 'error', tableId, regionIds: [child.id] })
    }
  }

  const occupant = new Map<string, string>()
  const reportedOverlaps = new Set<string>()
  for (const cell of model.cells) {
    if (cell.rowSpan < 1 || cell.colSpan < 1) {
      issues.push({ code: 'cell-invalid-span', severity: 'error', tableId, regionIds: [cell.regionId], row: cell.row, col: cell.col })
      continue
    }
    if (cell.row < 0 || cell.col < 0 || cell.row + cell.rowSpan > model.rows || cell.col + cell.colSpan > model.columns) {
      issues.push({ code: 'cell-index-out-of-range', severity: 'error', tableId, regionIds: [cell.regionId], row: cell.row, col: cell.col })
      continue
    }
    for (let r = cell.row; r < cell.row + cell.rowSpan; r++) {
      for (let c = cell.col; c < cell.col + cell.colSpan; c++) {
        const key = `${r}:${c}`
        const other = occupant.get(key)
        if (!other) {
          occupant.set(key, cell.regionId)
          continue
        }
        const pair = [other, cell.regionId].sort().join('|')
        if (reportedOverlaps.has(pair)) continue
        reportedOverlaps.add(pair)
        issues.push({ code: 'cell-overlap', severity: 'error', tableId, regionIds: [other, cell.regionId], row: r, col: c })
      }
    }
  }

  if (model.cells.length > 0) {
    for (let r = 0; r < model.rows; r++) {
      for (let c = 0; c < model.columns; c++) {
        if (!occupant.has(`${r}:${c}`)) {
          issues.push({ code: 'uncovered-position', severity: 'warning', tableId, regionIds: [tableId], row: r, col: c })
        }
      }
    }
  }

  return issues
}

export function validateTables(regions: Region[], parent?: Region): TableIssue[] {
  const issues: TableIssue[] = []
  for (const region of regions) {
    if (region.roles?.tableCellRole && !(parent && isTableRegion(parent))) {
      issues.push({ code: 'role-outside-table', severity: 'warning', regionIds: [region.id] })
    }
    if (isTableRegion(region)) issues.push(...validateTable(region))
    issues.push(...validateTables(region.regions ?? [], region))
  }
  return issues
}
