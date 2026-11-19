export type GridPoint = [number, number]
export type GridMatrix = GridPoint[][]

export interface TableAddPoints {
  row: number
  col: number
  left: GridPoint[]
  top: GridPoint[]
}

export interface CellSpan {
  row: number
  col: number
  rowSpan: number
  colSpan: number
}

export function interpolateGrid(
  corners: [GridPoint, GridPoint, GridPoint, GridPoint],
  rows: number,
  columns: number
): GridMatrix {
  const [topLeft, topRight, bottomRight, bottomLeft] = corners
  const grid: GridMatrix = []
  for (let r = 0; r <= rows; r++) {
    const v = r / rows
    const line: GridPoint[] = []
    for (let c = 0; c <= columns; c++) {
      const u = c / columns
      const x = (1 - u) * (1 - v) * topLeft[0] + u * (1 - v) * topRight[0] + u * v * bottomRight[0] + (1 - u) * v * bottomLeft[0]
      const y = (1 - u) * (1 - v) * topLeft[1] + u * (1 - v) * topRight[1] + u * v * bottomRight[1] + (1 - u) * v * bottomLeft[1]
      line.push([Math.round(x), Math.round(y)])
    }
    grid.push(line)
  }
  return grid
}

function orderedBetween(points: GridPoint[], from: GridPoint, to: GridPoint): GridPoint[] {
  const dx = to[0] - from[0]
  const dy = to[1] - from[1]
  const projection = (point: GridPoint) => (point[0] - from[0]) * dx + (point[1] - from[1]) * dy
  return [...points].sort((a, b) => projection(a) - projection(b))
}

function addPointsAt(addPoints: TableAddPoints[], row: number, col: number): TableAddPoints | undefined {
  return addPoints.find(entry => entry.row === row && entry.col === col)
}

function gridPoint(grid: GridMatrix, row: number, col: number): GridPoint {
  const point = grid[row]?.[col]
  if (!point) throw new RangeError(`Grid has no point at row ${row}, column ${col}`)
  return point
}

export function cellPolygonFromGrid(grid: GridMatrix, addPoints: TableAddPoints[], cell: CellSpan): GridPoint[] {
  const top = cell.row
  const bottom = cell.row + cell.rowSpan
  const left = cell.col
  const right = cell.col + cell.colSpan
  const outline: GridPoint[] = []

  for (let c = left; c < right; c++) {
    const from = gridPoint(grid, top, c)
    const to = gridPoint(grid, top, c + 1)
    outline.push(from, ...orderedBetween(addPointsAt(addPoints, top, c)?.top ?? [], from, to))
  }
  for (let r = top; r < bottom; r++) {
    const from = gridPoint(grid, r, right)
    const to = gridPoint(grid, r + 1, right)
    outline.push(from, ...orderedBetween(addPointsAt(addPoints, r, right)?.left ?? [], from, to))
  }
  for (let c = right; c > left; c--) {
    const from = gridPoint(grid, bottom, c)
    const to = gridPoint(grid, bottom, c - 1)
    outline.push(from, ...orderedBetween(addPointsAt(addPoints, bottom, c - 1)?.top ?? [], from, to))
  }
  for (let r = bottom; r > top; r--) {
    const from = gridPoint(grid, r, left)
    const to = gridPoint(grid, r - 1, left)
    outline.push(from, ...orderedBetween(addPointsAt(addPoints, r - 1, left)?.left ?? [], from, to))
  }

  const distinct = outline.filter((point, index) => index === 0 || !samePoint(point, outline[index - 1]!))
  while (distinct.length > 1 && samePoint(distinct[0]!, distinct[distinct.length - 1]!)) distinct.pop()
  return distinct
}

function samePoint(a: GridPoint, b: GridPoint): boolean {
  return a[0] === b[0] && a[1] === b[1]
}

export function rowLine(grid: GridMatrix, addPoints: TableAddPoints[], row: number): GridPoint[] {
  const line = grid[row] ?? []
  const points: GridPoint[] = []
  for (let c = 0; c < line.length; c++) {
    const from = line[c]!
    points.push(from)
    const to = line[c + 1]
    if (to) points.push(...orderedBetween(addPointsAt(addPoints, row, c)?.top ?? [], from, to))
  }
  return points
}

export function columnLine(grid: GridMatrix, addPoints: TableAddPoints[], col: number): GridPoint[] {
  const points: GridPoint[] = []
  for (let r = 0; r < grid.length; r++) {
    const from = grid[r]?.[col]
    if (!from) continue
    points.push(from)
    const to = grid[r + 1]?.[col]
    if (to) points.push(...orderedBetween(addPointsAt(addPoints, r, col)?.left ?? [], from, to))
  }
  return points
}

export function sharesRowOrColumn(a: CellSpan, b: CellSpan): boolean {
  const rowsOverlap = a.row < b.row + b.rowSpan && b.row < a.row + a.rowSpan
  const columnsOverlap = a.col < b.col + b.colSpan && b.col < a.col + a.colSpan
  return rowsOverlap || columnsOverlap
}
