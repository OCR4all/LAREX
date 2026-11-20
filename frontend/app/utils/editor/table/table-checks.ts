import { isTextRegion, type Region } from '@/models/editor'
import { isPointWithinOrOnPolygon } from '@/utils/editor/parent-constraint-utils'

export function linesCrossingCells(table: Region): Map<string, string[]> {
  const crossing = new Map<string, string[]>()
  for (const cell of table.regions ?? []) {
    if (!isTextRegion(cell) || cell.roles?.tableCellRole === undefined || cell.coords.points.length < 3) continue
    const outline = cell.coords.points.map(([x, y]) => ({ x, y }))
    const lineIds = (cell.textLines ?? [])
      .filter(line => line.coords.points.some(([x, y]) => !isPointWithinOrOnPolygon({ x, y }, outline)))
      .map(line => line.id)
    if (lineIds.length > 0) crossing.set(cell.id, lineIds)
  }
  return crossing
}
