import type { Command, CommandContext } from './types'
import { isTextRegion } from '@/models/editor'
import type { Region, TextLine, TextRegion } from '@/models/editor'
import { findRegionRecursive } from '@/utils/editor/pcgts-editor-primitives'
import { assignToShapes, centroid } from '@/utils/editor/table/table-builder'
import { isPointInPolygon } from '@/utils/editor/hit-detection'
import { commitDocument, shapePoints } from './table-command-utils'

export interface AssignTextLinesToCellsCommandData {
  tableId: string
}

interface OwnedLine {
  line: TextLine
  owner: TextRegion
}

function collectOutsideLines(regions: Region[], outline: { x: number, y: number }[], out: OwnedLine[]): void {
  for (const region of regions) {
    if (region.kind === 'TableRegion') continue
    if (isTextRegion(region)) {
      for (const line of region.textLines ?? []) {
        const [x, y] = centroid(shapePoints(line))
        if (isPointInPolygon({ x, y }, outline)) out.push({ line, owner: region })
      }
    }
    collectOutsideLines(region.regions ?? [], outline, out)
  }
}

function topToBottom(a: TextLine, b: TextLine): number {
  const [ax, ay] = centroid(shapePoints(a))
  const [bx, by] = centroid(shapePoints(b))
  return by - ay || ax - bx
}

export class AssignTextLinesToCellsCommand implements Command<{ moved: number }> {
  private tableId: string
  private previousLines = new Map<string, TextLine[] | undefined>()

  constructor(data: AssignTextLinesToCellsCommandData) {
    this.tableId = data.tableId
  }

  execute(ctx?: CommandContext): { moved: number } {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts) return { moved: 0 }

    const table = findRegionRecursive(pcGts.page.regions, this.tableId)?.region
    if (!table || table.kind !== 'TableRegion') return { moved: 0 }

    const cells = (table.regions ?? []).filter((region): region is TextRegion =>
      isTextRegion(region) && region.roles?.tableCellRole !== undefined)
    if (cells.length === 0) return { moved: 0 }

    const candidates: OwnedLine[] = cells.flatMap(cell => (cell.textLines ?? []).map(line => ({ line, owner: cell })))
    collectOutsideLines(pcGts.page.regions, table.coords.points.map(([x, y]) => ({ x, y })), candidates)

    const assignment = assignToShapes(
      candidates.map(({ line }) => ({ id: line.id, points: shapePoints(line) })),
      cells.map(cell => ({ id: cell.id, points: shapePoints(cell) })),
      false
    )
    const cellsById = new Map(cells.map(cell => [cell.id, cell]))
    const moving = candidates.filter(({ line, owner }) => {
      const target = assignment.get(line.id)
      return target !== undefined && target !== owner.id
    })
    if (moving.length === 0) return { moved: 0 }

    this.previousLines.clear()
    const remember = (region: TextRegion) => {
      if (!this.previousLines.has(region.id)) this.previousLines.set(region.id, region.textLines)
    }
    const movingIds = new Set(moving.map(({ line }) => line.id))
    for (const { owner } of moving) {
      remember(owner)
      owner.textLines = (owner.textLines ?? []).filter(line => !movingIds.has(line.id))
    }
    const receivers = new Set<TextRegion>()
    for (const { line } of moving) {
      const target = cellsById.get(assignment.get(line.id)!)!
      remember(target)
      target.textLines = [...(target.textLines ?? []), line]
      receivers.add(target)
    }
    for (const cell of receivers) cell.textLines = [...cell.textLines!].sort(topToBottom)

    commitDocument(session, pcGts)
    return { moved: moving.length }
  }

  undo(ctx?: CommandContext): void {
    const session = ctx?.session
    const pcGts = session?.document.value
    if (!session || !pcGts || this.previousLines.size === 0) return
    for (const [regionId, lines] of this.previousLines) {
      const region = findRegionRecursive(pcGts.page.regions, regionId)?.region
      if (region && isTextRegion(region)) region.textLines = lines
    }
    commitDocument(session, pcGts)
  }

  getDescription(): string {
    return 'Assign text lines to table cells'
  }
}
