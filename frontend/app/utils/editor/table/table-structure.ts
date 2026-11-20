import type { GridPoint, TableAddPoints } from './table-geometry'
import type { TableCell, TableGridModel, TableModel } from './table-model'

export type TableOperation
  = | { kind: 'split-row', row: number }
    | { kind: 'split-column', col: number }
    | { kind: 'merge-row-with-next', row: number }
    | { kind: 'merge-column-with-next', col: number }
    | { kind: 'delete-row', row: number }
    | { kind: 'delete-column', col: number }
    | { kind: 'merge-cells', cellIds: string[] }
    | { kind: 'split-cell', cellId: string }
    | { kind: 'set-header-row', row: number, header: boolean }

export interface TableOperationResult {
  model: TableModel
  created: string[]
  removed: string[]
  absorbed: string[]
  redistribute: string[]
}

type NewCellId = () => string

interface GriddedModel extends TableModel {
  grid: TableGridModel
}

function cloneModel(model: TableModel): GriddedModel {
  if (!model.grid) throw new Error('This table has no grid')
  return {
    ...model,
    grid: {
      points: model.grid.points.map(line => line.map(([x, y]) => [x, y] as GridPoint)),
      addPoints: model.grid.addPoints.map(entry => ({ ...entry, left: [...entry.left], top: [...entry.top] }))
    },
    cells: model.cells.map(cell => ({ ...cell }))
  }
}

function transpose(model: GriddedModel): GriddedModel {
  const { points, addPoints } = model.grid
  return {
    ...model,
    rows: model.columns,
    columns: model.rows,
    grid: {
      points: points[0]!.map((_, col) => points.map(line => line[col]!)),
      addPoints: addPoints.map(entry => ({ row: entry.col, col: entry.row, left: entry.top, top: entry.left }))
    },
    cells: model.cells.map(cell => ({ ...cell, row: cell.col, col: cell.row, rowSpan: cell.colSpan, colSpan: cell.rowSpan }))
  }
}

function lastRow(cell: TableCell): number {
  return cell.row + cell.rowSpan - 1
}

function sortCells(cells: TableCell[]): TableCell[] {
  return cells.sort((a, b) => a.row - b.row || a.col - b.col)
}

function assertRow(model: TableModel, row: number): void {
  if (!Number.isInteger(row) || row < 0 || row >= model.rows) throw new RangeError(`Table has no row ${row}`)
}

function distance(a: GridPoint, b: GridPoint): number {
  return Math.hypot(a[0] - b[0], a[1] - b[1])
}

function orderAlong(points: GridPoint[], from: GridPoint, to: GridPoint): GridPoint[] {
  const dx = to[0] - from[0]
  const dy = to[1] - from[1]
  const projection = (point: GridPoint) => (point[0] - from[0]) * dx + (point[1] - from[1]) * dy
  return [...points].sort((a, b) => projection(a) - projection(b))
}

export function splitPolylineInHalf(from: GridPoint, between: GridPoint[], to: GridPoint): { middle: GridPoint, before: GridPoint[], after: GridPoint[] } {
  const path = [from, ...orderAlong(between, from, to), to]
  const lengths = path.slice(1).map((point, index) => distance(path[index]!, point))
  let remaining = lengths.reduce((sum, length) => sum + length, 0) / 2
  for (let index = 0; index < lengths.length; index++) {
    const length = lengths[index]!
    if (remaining <= length && length > 0) {
      const t = remaining / length
      const start = path[index]!
      const end = path[index + 1]!
      const middle: GridPoint = [start[0] + (end[0] - start[0]) * t, start[1] + (end[1] - start[1]) * t]
      const inner = path.slice(1, -1)
      return {
        middle,
        before: inner.slice(0, index).filter(point => distance(point, middle) > 0),
        after: inner.slice(index).filter(point => distance(point, middle) > 0)
      }
    }
    remaining -= length
  }
  return { middle: [(from[0] + to[0]) / 2, (from[1] + to[1]) / 2], before: [], after: [] }
}

function entryAt(grid: TableGridModel, row: number, col: number): TableAddPoints {
  let entry = grid.addPoints.find(candidate => candidate.row === row && candidate.col === col)
  if (!entry) {
    entry = { row, col, left: [], top: [] }
    grid.addPoints.push(entry)
  }
  return entry
}

function pruneAddPoints(grid: TableGridModel): void {
  grid.addPoints = grid.addPoints
    .filter(entry => entry.left.length > 0 || entry.top.length > 0)
    .sort((a, b) => a.row - b.row || a.col - b.col)
}

function insertRowLine(grid: TableGridModel, row: number): void {
  const upper = grid.points[row]!
  const lower = grid.points[row + 1]!
  const halves = upper.map((from, col) => splitPolylineInHalf(from, grid.addPoints.find(e => e.row === row && e.col === col)?.left ?? [], lower[col]!))

  for (const entry of grid.addPoints) {
    if (entry.row > row) entry.row++
  }
  const lowerEntries: TableAddPoints[] = []
  halves.forEach((half, col) => {
    const existing = grid.addPoints.find(e => e.row === row && e.col === col)
    if (existing) existing.left = half.before
    if (half.after.length > 0) lowerEntries.push({ row: row + 1, col, left: half.after, top: [] })
  })
  grid.addPoints.push(...lowerEntries)
  grid.points.splice(row + 1, 0, halves.map(half => half.middle))
  pruneAddPoints(grid)
}

function removeRowLine(grid: TableGridModel, line: number): void {
  const last = grid.points.length - 1
  if (line > 0 && line < last) {
    grid.points[line]!.forEach((vertex, col) => {
      const below = grid.addPoints.find(e => e.row === line && e.col === col)?.left ?? []
      const above = entryAt(grid, line - 1, col)
      above.left = [...above.left, vertex, ...below]
    })
  }
  if (line === last) {
    for (const entry of grid.addPoints) {
      if (entry.row === last - 1) entry.left = []
    }
  }
  grid.addPoints = grid.addPoints.filter(entry => entry.row !== line)
  for (const entry of grid.addPoints) {
    if (entry.row > line) entry.row--
  }
  grid.points.splice(line, 1)
  pruneAddPoints(grid)
}

function emptyResult(model: TableModel): TableOperationResult {
  return { model, created: [], removed: [], absorbed: [], redistribute: [] }
}

function splitRow(source: GriddedModel, row: number, newCellId: NewCellId): TableOperationResult {
  assertRow(source, row)
  const model = source
  const result = emptyResult(model)
  insertRowLine(model.grid, row)

  const added: TableCell[] = []
  for (const cell of model.cells) {
    if (cell.row > row) {
      cell.row++
    } else if (lastRow(cell) >= row) {
      if (cell.row === row && cell.rowSpan === 1) {
        const id = newCellId()
        added.push({ ...cell, regionId: id, row: row + 1 })
        result.created.push(id)
        result.redistribute.push(cell.regionId)
      } else {
        cell.rowSpan++
      }
    }
  }
  model.cells = sortCells([...model.cells, ...added])
  model.rows++
  return result
}

function mergeRowWithNext(model: GriddedModel, row: number): TableOperationResult {
  assertRow(model, row)
  if (row === model.rows - 1) throw new RangeError('The last row has no next row to merge with')
  const result = emptyResult(model)

  const above = model.cells.filter(cell => lastRow(cell) === row)
  const below = model.cells.filter(cell => cell.row === row + 1)
  const pairs = above.map(top => ({ top, bottom: below.find(cell => cell.col === top.col && cell.colSpan === top.colSpan) }))
  if (pairs.some(pair => !pair.bottom) || pairs.length !== below.length) {
    throw new Error('The cells on both sides of this row line do not line up')
  }

  const absorbed = new Set<string>()
  for (const { top, bottom } of pairs) {
    top.rowSpan += bottom!.rowSpan
    absorbed.add(bottom!.regionId)
    result.absorbed.push(bottom!.regionId)
  }
  model.cells = model.cells.filter(cell => !absorbed.has(cell.regionId))
  for (const cell of model.cells) {
    if (cell.row > row + 1) cell.row--
    else if (cell.row <= row && lastRow(cell) > row) cell.rowSpan--
  }
  removeRowLine(model.grid, row + 1)
  model.rows--
  return result
}

function deleteRow(model: GriddedModel, row: number): TableOperationResult {
  assertRow(model, row)
  if (model.rows === 1) throw new Error('A table needs at least one row')
  const result = emptyResult(model)

  const removed = new Set(model.cells.filter(cell => cell.row === row && cell.rowSpan === 1).map(cell => cell.regionId))
  result.removed.push(...removed)
  model.cells = model.cells.filter(cell => !removed.has(cell.regionId))
  for (const cell of model.cells) {
    if (cell.row > row) cell.row--
    else if (lastRow(cell) >= row) cell.rowSpan--
  }
  removeRowLine(model.grid, row === model.rows - 1 ? row + 1 : row)
  model.rows--
  return result
}

function mergeCells(model: GriddedModel, cellIds: string[]): TableOperationResult {
  const ids = new Set(cellIds)
  const cells = model.cells.filter(cell => ids.has(cell.regionId))
  if (cells.length < 2 || cells.length !== ids.size) throw new Error('Select at least two cells of the same table')

  const top = Math.min(...cells.map(cell => cell.row))
  const left = Math.min(...cells.map(cell => cell.col))
  const bottom = Math.max(...cells.map(lastRow))
  const right = Math.max(...cells.map(cell => cell.col + cell.colSpan - 1))
  const area = cells.reduce((sum, cell) => sum + cell.rowSpan * cell.colSpan, 0)
  const outside = model.cells.some(cell => !ids.has(cell.regionId)
    && cell.row <= bottom && lastRow(cell) >= top && cell.col <= right && cell.col + cell.colSpan - 1 >= left)
  if (outside || area !== (bottom - top + 1) * (right - left + 1)) {
    throw new Error('The selected cells must form a rectangle')
  }

  const kept = sortCells([...cells])[0]!
  const result = emptyResult(model)
  result.absorbed.push(...cells.filter(cell => cell !== kept).map(cell => cell.regionId))
  kept.row = top
  kept.col = left
  kept.rowSpan = bottom - top + 1
  kept.colSpan = right - left + 1
  model.cells = model.cells.filter(cell => cell === kept || !ids.has(cell.regionId))
  return result
}

function splitCell(model: GriddedModel, cellId: string, newCellId: NewCellId): TableOperationResult {
  const cell = model.cells.find(candidate => candidate.regionId === cellId)
  if (!cell) throw new Error(`Table has no cell ${cellId}`)
  if (cell.rowSpan === 1 && cell.colSpan === 1) throw new Error('This cell does not span several rows or columns')

  const result = emptyResult(model)
  for (let row = cell.row; row <= lastRow(cell); row++) {
    for (let col = cell.col; col < cell.col + cell.colSpan; col++) {
      if (row === cell.row && col === cell.col) continue
      const id = newCellId()
      model.cells.push({ regionId: id, row, col, rowSpan: 1, colSpan: 1, header: cell.header })
      result.created.push(id)
    }
  }
  cell.rowSpan = 1
  cell.colSpan = 1
  result.redistribute.push(cell.regionId)
  sortCells(model.cells)
  return result
}

function setHeaderRow(model: GriddedModel, row: number, header: boolean): TableOperationResult {
  assertRow(model, row)
  for (const cell of model.cells) {
    if (cell.row <= row && row <= lastRow(cell)) cell.header = header
  }
  return emptyResult(model)
}

function onColumns(model: GriddedModel, operation: (transposed: GriddedModel) => TableOperationResult): TableOperationResult {
  const result = operation(transpose(model))
  const restored = transpose(result.model as GriddedModel)
  sortCells(restored.cells)
  return { ...result, model: restored }
}

export function applyTableOperation(model: TableModel, operation: TableOperation, newCellId: NewCellId): TableOperationResult {
  const draft = cloneModel(model)
  switch (operation.kind) {
    case 'split-row': return splitRow(draft, operation.row, newCellId)
    case 'split-column': return onColumns(draft, transposed => splitRow(transposed, operation.col, newCellId))
    case 'merge-row-with-next': return mergeRowWithNext(draft, operation.row)
    case 'merge-column-with-next': return onColumns(draft, transposed => mergeRowWithNext(transposed, operation.col))
    case 'delete-row': return deleteRow(draft, operation.row)
    case 'delete-column': return onColumns(draft, transposed => deleteRow(transposed, operation.col))
    case 'merge-cells': return mergeCells(draft, operation.cellIds)
    case 'split-cell': return splitCell(draft, operation.cellId, newCellId)
    case 'set-header-row': return setHeaderRow(draft, operation.row, operation.header)
  }
}

export function cellsRemovedBy(model: TableModel, operation: TableOperation): string[] {
  try {
    return applyTableOperation(model, operation, () => '').removed
  } catch {
    return []
  }
}

export function canApplyTableOperation(model: TableModel, operation: TableOperation): boolean {
  try {
    applyTableOperation(model, operation, () => '')
    return true
  } catch {
    return false
  }
}

const SAME_POINT_TOLERANCE = 1e-9

function isSamePoint(point: GridPoint, target: GridPoint): boolean {
  return Math.abs(point[0] - target[0]) <= SAME_POINT_TOLERANCE && Math.abs(point[1] - target[1]) <= SAME_POINT_TOLERANCE
}

export function gridHasPoint(model: TableModel, point: GridPoint): boolean {
  if (!model.grid) return false
  return model.grid.points.some(line => line.some(candidate => isSamePoint(candidate, point)))
    || model.grid.addPoints.some(entry => [...entry.left, ...entry.top].some(candidate => isSamePoint(candidate, point)))
}

export function moveGridPoint(model: TableModel, from: GridPoint, to: GridPoint): TableModel | undefined {
  if (!gridHasPoint(model, from)) return undefined
  const draft = cloneModel(model)
  const move = (point: GridPoint): GridPoint => isSamePoint(point, from) ? [to[0], to[1]] : point
  draft.grid.points = draft.grid.points.map(line => line.map(move))
  for (const entry of draft.grid.addPoints) {
    entry.left = entry.left.map(move)
    entry.top = entry.top.map(move)
  }
  return draft
}

function distanceToSegment(point: GridPoint, from: GridPoint, to: GridPoint): number {
  const dx = to[0] - from[0]
  const dy = to[1] - from[1]
  const lengthSquared = dx * dx + dy * dy
  const t = lengthSquared === 0 ? 0 : Math.max(0, Math.min(1, ((point[0] - from[0]) * dx + (point[1] - from[1]) * dy) / lengthSquared))
  return distance(point, [from[0] + dx * t, from[1] + dy * t])
}

function distanceToPolyline(point: GridPoint, path: GridPoint[]): number {
  let best = Infinity
  for (let index = 1; index < path.length; index++) best = Math.min(best, distanceToSegment(point, path[index - 1]!, path[index]!))
  return best
}

const ON_LINE_TOLERANCE = 1e-6

export function insertGridAddPoint(model: TableModel, at: GridPoint, to: GridPoint): TableModel | undefined {
  if (!model.grid || gridHasPoint(model, at)) return undefined
  const draft = cloneModel(model)
  const { points } = draft.grid
  let best: { distance: number, row: number, col: number, side: 'left' | 'top' } | undefined
  const consider = (row: number, col: number, side: 'left' | 'top', from: GridPoint, end: GridPoint) => {
    const between = draft.grid.addPoints.find(e => e.row === row && e.col === col)?.[side] ?? []
    const gap = distanceToPolyline(at, [from, ...orderAlong(between, from, end), end])
    if (gap <= ON_LINE_TOLERANCE && (!best || gap < best.distance)) best = { distance: gap, row, col, side }
  }
  for (let row = 0; row < points.length; row++) {
    for (let col = 0; col < points[row]!.length; col++) {
      const right = points[row]![col + 1]
      const below = points[row + 1]?.[col]
      if (right) consider(row, col, 'top', points[row]![col]!, right)
      if (below) consider(row, col, 'left', points[row]![col]!, below)
    }
  }
  if (!best) return undefined
  const entry = entryAt(draft.grid, best.row, best.col)
  entry[best.side] = [...entry[best.side], [to[0], to[1]]]
  pruneAddPoints(draft.grid)
  return draft
}

export function removeGridAddPoints(model: TableModel, removed: GridPoint[]): TableModel | undefined {
  if (!model.grid) return undefined
  const draft = cloneModel(model)
  if (removed.some(point => draft.grid.points.some(line => line.some(candidate => isSamePoint(candidate, point))))) {
    throw new Error('Grid corners cannot be deleted. Merge or delete rows and columns instead.')
  }
  const keep = (point: GridPoint) => !removed.some(target => isSamePoint(point, target))
  for (const entry of draft.grid.addPoints) {
    entry.left = entry.left.filter(keep)
    entry.top = entry.top.filter(keep)
  }
  pruneAddPoints(draft.grid)
  return draft
}

export interface GridLineRef {
  axis: 'row' | 'column'
  index: number
}

function rowLinePath(grid: TableGridModel, line: number): GridPoint[] {
  const corners = grid.points[line]!
  return corners.flatMap((corner, col) => {
    const next = corners[col + 1]
    if (!next) return [corner]
    const between = grid.addPoints.find(e => e.row === line && e.col === col)?.top ?? []
    return [corner, ...orderAlong(between, corner, next)]
  })
}

export function gridLineAt(model: TableModel, point: GridPoint): GridLineRef | undefined {
  if (!model.grid) return undefined
  const rows = cloneModel(model)
  const columns = transpose(rows)
  let best: { gap: number, ref: GridLineRef } | undefined
  for (const [axis, grid] of [['row', rows.grid], ['column', columns.grid]] as const) {
    grid.points.forEach((_, index) => {
      const gap = distanceToPolyline(point, rowLinePath(grid, index))
      if (gap <= ON_LINE_TOLERANCE && (!best || gap < best.gap)) best = { gap, ref: { axis, index } }
    })
  }
  return best?.ref
}

export function gridLinePoints(model: TableModel, ref: GridLineRef): GridPoint[] {
  if (!model.grid) return []
  const draft = cloneModel(model)
  const grid = ref.axis === 'row' ? draft.grid : transpose(draft).grid
  return grid.points[ref.index] ? rowLinePath(grid, ref.index) : []
}

function isStrictlyBetween(a: number, value: number, b: number): boolean {
  return (a - value) * (value - b) > 0
}

function shiftRowLine(grid: TableGridModel, line: number, delta: number, coordinate: 0 | 1): boolean {
  const shift = (point: GridPoint): GridPoint => coordinate === 0 ? [point[0] + delta, point[1]] : [point[0], point[1] + delta]
  const moved = grid.points[line]!.map(shift)
  const above = grid.points[line - 1]
  const below = grid.points[line + 1]
  const keepsOrder = moved.every((corner, col) => {
    const original = grid.points[line]![col]!
    const segments = [
      above && { from: above[col]!, to: corner, before: above[col]![coordinate] - original[coordinate], row: line - 1 },
      below && { from: corner, to: below[col]!, before: original[coordinate] - below[col]![coordinate], row: line }
    ].filter(segment => !!segment)
    return segments.every(({ from, to, before, row }) => {
      if ((from[coordinate] - to[coordinate]) * before <= 0) return false
      const between = grid.addPoints.find(e => e.row === row && e.col === col)?.left ?? []
      return between.every(point => isStrictlyBetween(from[coordinate], point[coordinate], to[coordinate]))
    })
  })
  if (!keepsOrder) return false
  grid.points[line] = moved
  for (const entry of grid.addPoints) {
    if (entry.row === line) entry.top = entry.top.map(shift)
  }
  return true
}

export function moveGridLine(model: TableModel, ref: GridLineRef, delta: number): TableModel | undefined {
  if (!model.grid || !Number.isInteger(ref.index) || ref.index < 0) return undefined
  const draft = cloneModel(model)
  if (ref.axis === 'row') {
    if (ref.index >= draft.grid.points.length) return undefined
    return shiftRowLine(draft.grid, ref.index, delta, 1) ? draft : undefined
  }
  const transposed = transpose(draft)
  if (ref.index >= transposed.grid.points.length) return undefined
  if (!shiftRowLine(transposed.grid, ref.index, delta, 0)) return undefined
  return transpose(transposed)
}
