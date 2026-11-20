import type { Region } from '@/models/editor/region'
import { isPointWithinOrOnPolygon } from '@/utils/editor/parent-constraint-utils'
import { centroid } from './table-builder'
import { cellPolygonFromGrid, type GridPoint } from './table-geometry'
import { buildTableModel, readGridMatrix, type TableCell, type TableModel } from './table-model'

export interface TableRepairPlan {
  model: TableModel
  missing: { row: number, col: number }[]
}

function positionsOf(cell: TableCell): string[] {
  const keys: string[] = []
  for (let row = cell.row; row < cell.row + cell.rowSpan; row++) {
    for (let col = cell.col; col < cell.col + cell.colSpan; col++) keys.push(`${row}:${col}`)
  }
  return keys
}

export function planTableRepair(table: Region): TableRepairPlan | undefined {
  if (table.kind !== 'TableRegion') return undefined
  const base = buildTableModel(table)
  if (readGridMatrix(table) && !base.grid) return undefined
  if (base.rows < 1 || base.columns < 1) return undefined

  const occupied = new Set<string>()
  const cells: TableCell[] = []
  for (const original of base.cells) {
    if (original.row < 0 || original.col < 0 || original.row >= base.rows || original.col >= base.columns) continue
    const cell: TableCell = {
      ...original,
      rowSpan: Math.min(Math.max(1, original.rowSpan), base.rows - original.row),
      colSpan: Math.min(Math.max(1, original.colSpan), base.columns - original.col)
    }
    const keys = positionsOf(cell)
    if (keys.some(key => occupied.has(key))) continue
    keys.forEach(key => occupied.add(key))
    cells.push(cell)
  }

  const missing: { row: number, col: number }[] = []
  if (base.grid) {
    const { points, addPoints } = base.grid
    const outlineAt = (row: number, col: number) => cellPolygonFromGrid(points, addPoints, { row, col, rowSpan: 1, colSpan: 1 })
      .map(([x, y]) => ({ x, y }))
    const known = new Set(base.cells.map(cell => cell.regionId))
    for (const child of table.regions ?? []) {
      if (known.has(child.id) || child.coords.points.length === 0) continue
      const [x, y] = centroid(child.coords.points.map(([px, py]) => [px, py] as GridPoint))
      for (let row = 0; row < base.rows && !known.has(child.id); row++) {
        for (let col = 0; col < base.columns; col++) {
          if (occupied.has(`${row}:${col}`) || !isPointWithinOrOnPolygon({ x, y }, outlineAt(row, col))) continue
          cells.push({ regionId: child.id, row, col, rowSpan: 1, colSpan: 1, header: false })
          occupied.add(`${row}:${col}`)
          known.add(child.id)
          break
        }
      }
    }
    for (let row = 0; row < base.rows; row++) {
      for (let col = 0; col < base.columns; col++) {
        if (!occupied.has(`${row}:${col}`)) missing.push({ row, col })
      }
    }
  }

  return { model: { ...base, cells: cells.sort((a, b) => a.row - b.row || a.col - b.col) }, missing }
}
