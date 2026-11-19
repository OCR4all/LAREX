import { Polygon } from '@/models/editor/geometry'
import type { Region } from '@/models/editor/region'
import {
  cellPolygonFromGrid,
  columnLine,
  rowLine,
  type GridMatrix,
  type GridPoint,
  type TableAddPoints
} from './table-geometry'

export interface TableCell {
  regionId: string
  row: number
  col: number
  rowSpan: number
  colSpan: number
  header: boolean
}

export interface TableGridModel {
  points: GridMatrix
  addPoints: TableAddPoints[]
}

export interface TableModel {
  tableId: string
  rows: number
  columns: number
  grid?: TableGridModel
  cells: TableCell[]
}

export function isTableRegion(region: Region): boolean {
  return region.kind === 'TableRegion'
}

export function readGridMatrix(table: Region): GridMatrix | undefined {
  const gridRows = table.grid?.rows
  if (!gridRows || gridRows.length < 2) return undefined

  const indexedCount = gridRows.filter(row => row.index !== undefined).length
  if (indexedCount > 0 && indexedCount < gridRows.length) return undefined
  const indexed = indexedCount > 0
  const ordered = indexed ? [...gridRows].sort((a, b) => a.index! - b.index!) : gridRows
  if (indexed && ordered.some((row, position) => row.index !== position)) return undefined

  const matrix = ordered.map(row => row.points.points.map(([x, y]) => [x, y] as GridPoint))
  const width = matrix[0]!.length
  if (width < 2 || matrix.some(line => line.length !== width)) return undefined
  return matrix
}

function addPointsSide(points: Polygon | undefined, text: string | undefined): GridPoint[] | undefined {
  if (points) return points.points.map(([x, y]) => [x, y])
  return text === undefined || text.trim() === '' ? [] : undefined
}

export function readAddPoints(table: Region): TableAddPoints[] | undefined {
  const result: TableAddPoints[] = []
  for (const entry of table.grid?.addPoints ?? []) {
    if (entry.row === undefined || entry.col === undefined) return undefined
    const left = addPointsSide(entry.leftPoints, entry.left)
    const top = addPointsSide(entry.topPoints, entry.top)
    if (!left || !top) return undefined
    result.push({ row: entry.row, col: entry.col, left, top })
  }
  return result
}

function cellOf(region: Region): TableCell | undefined {
  const role = region.roles?.tableCellRole
  if (role?.rowIndex === undefined || role.columnIndex === undefined) return undefined
  return {
    regionId: region.id,
    row: role.rowIndex,
    col: role.columnIndex,
    rowSpan: role.rowSpan ?? 1,
    colSpan: role.colSpan ?? 1,
    header: role.header ?? false
  }
}

export function readTableCells(table: Region): TableCell[] {
  return (table.regions ?? []).map(cellOf).filter((cell): cell is TableCell => cell !== undefined)
}

export function tableDimensions(table: Region): { rows: number, columns: number } | undefined {
  const matrix = readGridMatrix(table)
  if (matrix) return { rows: matrix.length - 1, columns: matrix[0]!.length - 1 }

  const cells = readTableCells(table)
  if (cells.length === 0) return undefined
  return {
    rows: Math.max(...cells.map(cell => cell.row + Math.max(cell.rowSpan, 1))),
    columns: Math.max(...cells.map(cell => cell.col + Math.max(cell.colSpan, 1)))
  }
}

export function buildTableModel(table: Region): TableModel {
  const matrix = readGridMatrix(table)
  const addPoints = matrix ? readAddPoints(table) : undefined
  const dimensions = tableDimensions(table)
  return {
    tableId: table.id,
    rows: dimensions?.rows ?? table.rows ?? 0,
    columns: dimensions?.columns ?? table.columns ?? 0,
    grid: matrix && addPoints ? { points: matrix, addPoints } : undefined,
    cells: readTableCells(table)
  }
}

export function syncTableDimensions(table: Region): boolean {
  if (!isTableRegion(table)) return false
  const dimensions = tableDimensions(table)
  if (!dimensions) return false
  if (table.rows === dimensions.rows && table.columns === dimensions.columns) return false
  table.rows = dimensions.rows
  table.columns = dimensions.columns
  return true
}

export function findParentTable(regions: Region[] | undefined, regionId: string, parent?: Region): Region | undefined {
  for (const region of regions ?? []) {
    if (region.id === regionId) return parent && isTableRegion(parent) ? parent : undefined
    const found = findParentTable(region.regions, regionId, region)
    if (found) return found
  }
  return undefined
}

export function interiorGridLines(model: TableModel): GridPoint[][] {
  if (!model.grid) return []
  const { points, addPoints } = model.grid
  const lines: GridPoint[][] = []
  for (let row = 1; row < model.rows; row++) lines.push(rowLine(points, addPoints, row))
  for (let col = 1; col < model.columns; col++) lines.push(columnLine(points, addPoints, col))
  return lines
}

export function tableCellPosition(table: Region, cell: Region): TableCell | undefined {
  return isTableRegion(table) ? cellOf(cell) : undefined
}

export function cellPolygon(model: TableModel, cell: TableCell): GridPoint[] | undefined {
  if (!model.grid) return undefined
  return cellPolygonFromGrid(model.grid.points, model.grid.addPoints, cell)
}

export function applyTableModel(table: Region, model: TableModel): string[] {
  const changed = new Set<string>([table.id])
  table.rows = model.rows
  table.columns = model.columns

  if (model.grid) {
    table.grid = {
      rows: model.grid.points.map((line, index) => ({ index, points: new Polygon(line.map(([x, y]) => [x, y])) })),
      addPoints: model.grid.addPoints.length > 0
        ? model.grid.addPoints.map(entry => ({
            row: entry.row,
            col: entry.col,
            leftPoints: entry.left.length > 0 ? new Polygon(entry.left.map(([x, y]) => [x, y])) : undefined,
            topPoints: entry.top.length > 0 ? new Polygon(entry.top.map(([x, y]) => [x, y])) : undefined
          }))
        : undefined
    }
  }

  const children = new Map((table.regions ?? []).map(child => [child.id, child]))
  for (const cell of model.cells) {
    const child = children.get(cell.regionId)
    if (!child) throw new Error(`Table ${table.id} has no cell region ${cell.regionId}`)

    const previous = child.roles?.tableCellRole
    child.roles = {
      ...child.roles,
      tableCellRole: {
        rowIndex: cell.row,
        columnIndex: cell.col,
        rowSpan: cell.rowSpan !== 1 || previous?.rowSpan !== undefined ? cell.rowSpan : undefined,
        colSpan: cell.colSpan !== 1 || previous?.colSpan !== undefined ? cell.colSpan : undefined,
        header: cell.header || previous?.header !== undefined ? cell.header : undefined
      }
    }
    changed.add(child.id)

    const outline = cellPolygon(model, cell)
    if (outline) child.coords = new Polygon(outline, child.coords.conf)
  }

  return [...changed]
}
