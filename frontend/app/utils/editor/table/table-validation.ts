import type { Region } from '@/models/editor/region'
import type { GridPoint } from './table-geometry'
import { linesCrossingCells } from './table-checks'
import { buildTableModel, cellPolygon, isTableRegion, type TableModel } from './table-model'

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
    | 'cell-geometry-mismatch'
    | 'table-outline-mismatch'
    | 'line-crosses-cell'

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

const REPAIRABLE_CODES = new Set<TableIssueCode>([
  'dimension-mismatch',
  'cell-missing-role',
  'cell-invalid-span',
  'cell-index-out-of-range',
  'uncovered-position',
  'cell-geometry-mismatch',
  'table-outline-mismatch',
  'line-crosses-cell'
])

export function isRepairableTableIssue(issue: TableIssue): boolean {
  return REPAIRABLE_CODES.has(issue.code)
}

const ISSUE_MESSAGES: Record<TableIssueCode, string> = {
  'grid-too-few-rows': 'The grid needs at least two grid rows',
  'grid-row-indices': 'The grid row indices do not count up from 0',
  'grid-ragged': 'The grid rows have different numbers of points',
  'grid-too-few-columns': 'The grid rows need at least two points',
  'add-points-invalid': 'The grid AddPoints cannot be read',
  'add-points-out-of-range': 'Grid AddPoints refer to a row or column outside the grid',
  'dimension-mismatch': 'The rows and columns attributes do not match the grid or cells',
  'cell-missing-role': 'A region in the table has no table cell role',
  'cell-incomplete-role': 'A table cell role has no row or column index',
  'cell-invalid-span': 'A cell has a row or column span below 1',
  'cell-index-out-of-range': 'A cell lies outside the table',
  'cell-overlap': 'Two cells cover the same table position',
  'uncovered-position': 'A table position is not covered by any cell',
  'role-outside-table': 'A region with a table cell role is not inside a table',
  'cell-geometry-mismatch': 'A cell outline does not match the grid',
  'table-outline-mismatch': 'The table outline does not match the grid',
  'line-crosses-cell': 'Text lines cross the border of their cell'
}

export function describeTableIssue(issue: TableIssue): string {
  const position = issue.row !== undefined && issue.col !== undefined ? ` (row ${issue.row + 1}, column ${issue.col + 1})` : ''
  return `${ISSUE_MESSAGES[issue.code]}${position}.`
}

function outlineTolerance(points: GridPoint[]): number {
  const xs = points.map(point => point[0])
  const ys = points.map(point => point[1])
  return Math.hypot(Math.max(...xs) - Math.min(...xs), Math.max(...ys) - Math.min(...ys)) * 1e-6 + 1e-12
}

function distanceToOutline(point: GridPoint, outline: GridPoint[]): number {
  let best = Infinity
  outline.forEach((from, index) => {
    const to = outline[(index + 1) % outline.length]!
    const dx = to[0] - from[0]
    const dy = to[1] - from[1]
    const lengthSquared = dx * dx + dy * dy
    const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / lengthSquared))
    best = Math.min(best, Math.hypot(from[0] + dx * t - point[0], from[1] + dy * t - point[1]))
  })
  return best
}

function sameOutline(a: GridPoint[], b: GridPoint[], tolerance: number): boolean {
  if (a.length < 3) return false
  return a.every(point => distanceToOutline(point, b) <= tolerance) && b.every(point => distanceToOutline(point, a) <= tolerance)
}

function validateGeometry(table: Region, model: TableModel, cellsValid: boolean, issues: TableIssue[]): void {
  if (!model.grid || !cellsValid) return
  const outline = cellPolygon(model, { regionId: table.id, row: 0, col: 0, rowSpan: model.rows, colSpan: model.columns, header: false })!
  const tolerance = outlineTolerance(outline)
  const points = (region: Region): GridPoint[] => region.coords.points.map(([x, y]) => [x, y])
  if (!sameOutline(points(table), outline, tolerance)) {
    issues.push({ code: 'table-outline-mismatch', severity: 'warning', tableId: table.id, regionIds: [table.id] })
  }
  const children = new Map((table.regions ?? []).map(child => [child.id, child]))
  for (const cell of model.cells) {
    const child = children.get(cell.regionId)
    if (child && !sameOutline(points(child), cellPolygon(model, cell)!, tolerance)) {
      issues.push({ code: 'cell-geometry-mismatch', severity: 'warning', tableId: table.id, regionIds: [cell.regionId], row: cell.row, col: cell.col })
    }
  }
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

  const cellsValid = !issues.some(issue => issue.severity === 'error')
  validateGeometry(table, model, cellsValid, issues)

  const cellById = new Map(model.cells.map(cell => [cell.regionId, cell]))
  for (const [cellId, lineIds] of linesCrossingCells(table)) {
    const cell = cellById.get(cellId)
    issues.push({ code: 'line-crosses-cell', severity: 'warning', tableId, regionIds: lineIds, row: cell?.row, col: cell?.col })
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
