import { isPointInPolygon } from '@/utils/editor/hit-detection'
import { interpolateGrid, type GridPoint } from './table-geometry'
import type { TableCell, TableModel } from './table-model'

export interface TableShape {
  id: string
  points: GridPoint[]
}

interface Box {
  minX: number
  maxX: number
  minY: number
  maxY: number
}

function boxOf(points: GridPoint[]): Box {
  const xs = points.map(([x]) => x)
  const ys = points.map(([, y]) => y)
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) }
}

function distanceSquared(a: GridPoint, b: GridPoint): number {
  return (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2
}

export function outlineCorners(outline: GridPoint[]): [GridPoint, GridPoint, GridPoint, GridPoint] {
  const { minX, maxX, minY, maxY } = boxOf(outline)
  const boxCorners: [GridPoint, GridPoint, GridPoint, GridPoint] = [[minX, maxY], [maxX, maxY], [maxX, minY], [minX, minY]]
  const nearest = boxCorners.map(target => outline.reduce((best, point) =>
    distanceSquared(point, target) < distanceSquared(best, target) ? point : best)) as [GridPoint, GridPoint, GridPoint, GridPoint]
  const distinct = new Set(nearest.map(([x, y]) => `${x}:${y}`))
  return distinct.size === 4 ? nearest : boxCorners
}

export interface GridTableOptions {
  tableId: string
  outline: GridPoint[]
  rows: number
  columns: number
  headerRows: number
  newCellId: (row: number, col: number) => string
}

export function createGridTableModel(options: GridTableOptions): TableModel {
  const points = interpolateGrid(outlineCorners(options.outline), options.rows, options.columns)
  const cells: TableCell[] = []
  for (let row = 0; row < options.rows; row++) {
    for (let col = 0; col < options.columns; col++) {
      cells.push({ regionId: options.newCellId(row, col), row, col, rowSpan: 1, colSpan: 1, header: row < options.headerRows })
    }
  }
  return { tableId: options.tableId, rows: options.rows, columns: options.columns, grid: { points, addPoints: [] }, cells }
}

export function centroid(points: GridPoint[]): GridPoint {
  let area = 0
  let cx = 0
  let cy = 0
  for (let i = 0; i < points.length; i++) {
    const [x1, y1] = points[i]!
    const [x2, y2] = points[(i + 1) % points.length]!
    const cross = x1 * y2 - x2 * y1
    area += cross
    cx += (x1 + x2) * cross
    cy += (y1 + y2) * cross
  }
  if (Math.abs(area) < 1e-12) {
    const sum = points.reduce<GridPoint>((acc, [x, y]) => [acc[0] + x, acc[1] + y], [0, 0])
    return [sum[0] / points.length, sum[1] / points.length]
  }
  return [cx / (3 * area), cy / (3 * area)]
}

function contains(shape: TableShape, point: GridPoint): boolean {
  return isPointInPolygon({ x: point[0], y: point[1] }, shape.points.map(([x, y]) => ({ x, y })))
}

export function assignToShapes(items: TableShape[], targets: TableShape[], fallbackToNearest: boolean): Map<string, string> {
  const assignment = new Map<string, string>()
  if (targets.length === 0) return assignment
  const targetCentroids = targets.map(target => ({ id: target.id, center: centroid(target.points) }))

  for (const item of items) {
    if (item.points.length === 0) continue
    const center = centroid(item.points)
    const containing = targets.find(target => contains(target, center))
    if (containing) {
      assignment.set(item.id, containing.id)
      continue
    }
    if (!fallbackToNearest) continue
    const nearest = targetCentroids.reduce((best, candidate) =>
      distanceSquared(candidate.center, center) < distanceSquared(best.center, center) ? candidate : best)
    assignment.set(item.id, nearest.id)
  }
  return assignment
}

function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[middle]! : (sorted[middle - 1]! + sorted[middle]!) / 2
}

function clusterEdges(values: number[], tolerance: number): number[] {
  const sorted = [...values].sort((a, b) => a - b)
  const clusters: number[][] = []
  for (const value of sorted) {
    const current = clusters[clusters.length - 1]
    if (current && value - current[current.length - 1]! <= tolerance) current.push(value)
    else clusters.push([value])
  }
  return clusters.map(cluster => cluster.reduce((sum, value) => sum + value, 0) / cluster.length)
}

function nearestIndex(boundaries: number[], value: number): number {
  let best = 0
  for (let i = 1; i < boundaries.length; i++) {
    if (Math.abs(boundaries[i]! - value) < Math.abs(boundaries[best]! - value)) best = i
  }
  return best
}

interface Span {
  start: number
  span: number
}

function spanOf(boundaries: number[], from: number, to: number): Span {
  const lastBand = boundaries.length - 2
  const start = Math.min(nearestIndex(boundaries, from), lastBand)
  const end = nearestIndex(boundaries, to)
  return { start, span: Math.max(1, end - start) }
}

function dropEmptyBands(boundaries: number[], spans: Span[]): number[] {
  for (let band = 0; band < boundaries.length - 1; band++) {
    const covered = spans.some(span => span.start <= band && band < span.start + span.span)
    if (!covered && boundaries.length > 2) {
      const merged = (boundaries[band]! + boundaries[band + 1]!) / 2
      return [...boundaries.slice(0, band), merged, ...boundaries.slice(band + 2)]
    }
  }
  return boundaries
}

export interface InferredTable {
  model: TableModel
  outline: GridPoint[]
}

export function inferTableFromShapes(tableId: string, shapes: TableShape[]): InferredTable {
  if (shapes.length === 0) throw new Error('A table needs at least one cell')
  const boxes = shapes.map(shape => boxOf(shape.points))
  const toleranceY = 0.35 * median(boxes.map(box => box.maxY - box.minY))
  const toleranceX = 0.35 * median(boxes.map(box => box.maxX - box.minX))

  let ys = clusterEdges(boxes.flatMap(box => [box.minY, box.maxY]), toleranceY).reverse()
  let xs = clusterEdges(boxes.flatMap(box => [box.minX, box.maxX]), toleranceX)

  const rowSpans = () => boxes.map(box => spanOf(ys, box.maxY, box.minY))
  const columnSpans = () => boxes.map(box => spanOf(xs, box.minX, box.maxX))
  for (let next = dropEmptyBands(ys, rowSpans()); next !== ys; next = dropEmptyBands(ys, rowSpans())) ys = next
  for (let next = dropEmptyBands(xs, columnSpans()); next !== xs; next = dropEmptyBands(xs, columnSpans())) xs = next

  const rows = rowSpans()
  const columns = columnSpans()
  const cells: TableCell[] = shapes.map((shape, index) => ({
    regionId: shape.id,
    row: rows[index]!.start,
    col: columns[index]!.start,
    rowSpan: rows[index]!.span,
    colSpan: columns[index]!.span,
    header: false
  }))

  const points = ys.map(y => xs.map(x => [x, y] as GridPoint))
  const left = xs[0]!
  const right = xs[xs.length - 1]!
  const top = ys[0]!
  const bottom = ys[ys.length - 1]!
  return {
    model: { tableId, rows: ys.length - 1, columns: xs.length - 1, grid: { points, addPoints: [] }, cells },
    outline: [[left, top], [right, top], [right, bottom], [left, bottom]]
  }
}
